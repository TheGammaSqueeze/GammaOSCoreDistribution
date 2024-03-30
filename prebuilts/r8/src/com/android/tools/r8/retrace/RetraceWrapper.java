/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.r8.retrace;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class RetraceWrapper {

  /** Default paths to search for mapping files. */
  private static final List<String> AOSP_MAP_SEARCH_PATHS =
      Collections.singletonList("out/target/common/obj/APPS");

  private static final String USAGE =
      String.join(
          System.lineSeparator(),
          "Usage: retrace [<option>]* [<file>]",
          "where <file> is the file to retrace (default stdin)",
          "  and <option> is one of:",
          "  --default-map <file>     # Default map to retrace lines that don't auto-identify.",
          "  --map-search-path <path> # Path to search for mappings that support auto-identify.",
          "                           # Separate <path> entries by colon ':'.",
          "                           # Default '" + String.join(":", AOSP_MAP_SEARCH_PATHS) + "'.",
          "  --print-map-table        # Print the table of identified mapping files and exit.",
          "  -h, --help               # Print this message.");

  private static class ForwardingDiagnosticsHander implements DiagnosticsHandler {
    @Override
    public void error(Diagnostic error) {
      throw RetraceWrapper.error(error.getDiagnosticMessage());
    }

    @Override
    public void warning(Diagnostic warning) {
      RetraceWrapper.warning(warning.getDiagnosticMessage());
    }

    @Override
    public void info(Diagnostic info) {
      RetraceWrapper.info(info.getDiagnosticMessage());
    }
  }

  private static class LazyRetracer {
    final MapInfo mapInfo;
    final Path mapPath;

    private Retracer lazyRetracer = null;

    public LazyRetracer(MapInfo mapInfo, Path mapPath) {
      this.mapInfo = mapInfo;
      this.mapPath = mapPath;
    }

    public Retracer getRetracer() {
      if (lazyRetracer == null) {
        try {
          lazyRetracer =
              Retracer.createDefault(
                  ProguardMapProducer.fromPath(mapPath), new ForwardingDiagnosticsHander());
        } catch (InvalidMappingFileException e) {
          throw new RuntimeException("Failure in mapping file: " + mapPath, e);
        }
      }
      return lazyRetracer;
    }
  }

  private static class MapInfo {
    final String id;
    final String hash;

    public MapInfo(String id, String hash) {
      assert id != null;
      assert hash != null;
      this.id = id;
      this.hash = hash;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      MapInfo otherMapInfo = (MapInfo) other;
      return Objects.equals(id, otherMapInfo.id) && Objects.equals(hash, otherMapInfo.hash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, hash);
    }

    @Override
    public String toString() {
      return "MapInfo{" + "id='" + id + '\'' + ", hash='" + hash + '\'' + '}';
    }
  }

  /** Representation of a line with a hole, ala "<prefix><hole><suffix>". */
  private static class LineWithHole {
    final String line;
    final int start;
    final int end;

    public LineWithHole(String line, int start, int end) {
      this.line = line;
      this.start = start;
      this.end = end;
    }

    public String plug(String string) {
      return line.substring(0, start) + string + line.substring(end);
    }
  }

  /** Parsed exception header line, such as "Caused by: <exception>". */
  private static class ExceptionLine extends LineWithHole {
    final ClassReference exception;

    public ExceptionLine(String line, int start, int end, ClassReference exception) {
      super(line, start, end);
      this.exception = exception;
    }
  }

  /** Parsed frame line, such as "at <class>.<method>(<source-file>:<line>)". */
  private static class FrameLine extends LineWithHole {
    final ClassReference clazz;
    final String methodName;
    final String sourceFile;
    final OptionalInt lineNumber;

    public FrameLine(
        String line,
        int start,
        int end,
        ClassReference clazz,
        String methodName,
        String sourceFile,
        OptionalInt lineNumber) {
      super(line, start, end);
      this.clazz = clazz;
      this.methodName = methodName;
      this.sourceFile = sourceFile;
      this.lineNumber = lineNumber;
    }
  }

  /** An immutable linked list of the result lines so that a result tree can be created. */
  private static class ResultNode {
    final ResultNode parent;
    final String line;

    public ResultNode(ResultNode parent, String line) {
      this.parent = parent;
      this.line = line;
    }

    public void print() {
      if (parent != null) {
        parent.print();
      }
      System.out.println(line);
    }
  }

  /**
   * Indication that a line is the start of an escaping stack trace.
   *
   * <p>Note that this does not identify an exception that is directly printed with, e.g.,
   * Throwable.printStackTrace(), but only one that exits the runtime. That should generally catch
   * what we need, but could be refined to match ':' which is the only other indicator.
   */
  private static final String ESCAPING_EXCEPTION_MARKER = "Exception in thread \"";

  /** Indication that a line is the start of a "caused by" stack trace. */
  private static final String CAUSED_BY_EXCEPTION_MARKER = "Caused by: ";

  /** Indication that a line is the start of a "suppressed" stack trace. */
  private static final String SUPPRESSED_EXCEPTION_MARKER = "Suppressed: ";

  /** Start of the source file for any R8 build withing AOSP. */
  // TODO(zerny): Should this be a configurable prefix?
  private static final String AOSP_SF_MARKER = "go/retraceme ";

  /** Start of the source file for any R8 compiler build. */
  private static final String R8_SF_MARKER = "R8_";

  /** Mapping file header indicating the id of mapping file. */
  private static final String MAP_ID_HEADER_MARKER = "# pg_map_id: ";

  /** Mapping file header indicating the hash of mapping file. */
  private static final String MAP_HASH_HEADER_MARKER = "# pg_map_hash: SHA-256 ";

  /** Map of cached/lazy retracer instances for maps found in the local AOSP build. */
  private static final Map<String, LazyRetracer> RETRACERS = new HashMap<>();

  private static final List<String> PENDING_MESSAGES = new ArrayList<>();

  private static void flushPendingMessages() {
    PENDING_MESSAGES.forEach(System.err::println);
  }

  private static void info(String message) {
    PENDING_MESSAGES.add("Info: " + message);
  }

  private static void warning(String message) {
    PENDING_MESSAGES.add("Warning: " + message);
  }

  private static RuntimeException error(String message) {
    flushPendingMessages();
    throw new RuntimeException(message);
  }

  private static MapInfo readMapHeaderInfo(Path path) throws IOException {
    String mapId = null;
    String mapHash = null;
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      while (true) {
        String line = reader.readLine();
        if (line == null || !line.startsWith("#")) {
          break;
        }
        if (mapId == null) {
          mapId = tryParseMapIdHeader(line);
        }
        if (mapHash == null) {
          mapHash = tryParseMapHashHeader(line);
        }
      }
    }
    if (mapId != null && mapHash != null) {
      return new MapInfo(mapId, mapHash);
    }
    return null;
  }

  private static Path getProjectRoot() throws URISyntaxException {
    // The retrace.jar should be located in out/[soong/]host/<platform>/framework/retrace.jar
    Path hostPath = Paths.get("out", "host");
    Path hostSoongPath = Paths.get("out", "soong");
    Path retraceJarPath =
        Paths.get(RetraceWrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    for (Path current = retraceJarPath; current != null; current = current.getParent()) {
      if (current.endsWith(hostPath) || current.endsWith(hostSoongPath)) {
        return current.getParent().getParent();
      }
    }
    info(
        "Unable to determine the project root based on the retrace.jar location: "
            + retraceJarPath);
    return null;
  }

  private static LazyRetracer getRetracerForAosp(String sourceFile) {
    MapInfo stackLineInfo = tryParseSourceFileMarkerForAosp(sourceFile);
    return stackLineInfo == null ? null : RETRACERS.get(stackLineInfo.id);
  }

  private static LazyRetracer getRetracerForR8(String sourceFile) {
    MapInfo stackLineInfo = tryParseSourceFileMarkerForR8(sourceFile);
    if (stackLineInfo == null) {
      return null;
    }
    LazyRetracer retracer = RETRACERS.get(stackLineInfo.id);
    if (retracer == null) {
      // TODO(zerny): Lookup the mapping file in the R8 cloud storage bucket.
      info("Could not identify a mapping file for lines with R8 tag: " + stackLineInfo);
    }
    return retracer;
  }

  private static String tryParseMapIdHeader(String line) {
    return tryParseMapHeaderLine(line, MAP_ID_HEADER_MARKER);
  }

  private static String tryParseMapHashHeader(String line) {
    return tryParseMapHeaderLine(line, MAP_HASH_HEADER_MARKER);
  }

  private static String tryParseMapHeaderLine(String line, String headerMarker) {
    if (line.startsWith(headerMarker)) {
      return line.substring(headerMarker.length());
    }
    return null;
  }

  private static FrameLine tryParseFrameLine(String line) {
    String atMarker = "at ";
    int atIndex = line.indexOf(atMarker);
    if (atIndex < 0) {
      return null;
    }
    int parenStartIndex = line.indexOf('(', atIndex);
    if (parenStartIndex < 0) {
      return null;
    }
    int parenEndIndex = line.indexOf(')', parenStartIndex);
    if (parenEndIndex < 0) {
      return null;
    }
    int classAndMethodSeperatorIndex = line.lastIndexOf('.', parenStartIndex);
    if (classAndMethodSeperatorIndex < 0) {
      return null;
    }
    int classStartIndex = atIndex + atMarker.length();
    String clazz = line.substring(classStartIndex, classAndMethodSeperatorIndex);
    String method = line.substring(classAndMethodSeperatorIndex + 1, parenStartIndex);
    // Source file and line may or may not be present.
    int sourceAndLineSeperatorIndex = line.lastIndexOf(':', parenEndIndex);
    String sourceFile;
    OptionalInt lineNumber;
    if (parenStartIndex < sourceAndLineSeperatorIndex) {
      sourceFile = line.substring(parenStartIndex + 1, sourceAndLineSeperatorIndex);
      try {
        lineNumber =
            OptionalInt.of(
                Integer.parseInt(line.substring(sourceAndLineSeperatorIndex + 1, parenEndIndex)));
      } catch (NumberFormatException e) {
        lineNumber = OptionalInt.empty();
      }
    } else {
      sourceFile = line.substring(parenStartIndex + 1, parenEndIndex);
      lineNumber = OptionalInt.empty();
    }
    return new FrameLine(
        line,
        classStartIndex,
        parenEndIndex + 1,
        Reference.classFromTypeName(clazz),
        method,
        sourceFile,
        lineNumber);
  }

  private static int indexOfExceptionStart(String line) {
    int i = line.indexOf(ESCAPING_EXCEPTION_MARKER);
    if (i >= 0) {
      int start = line.indexOf("\" ", i + ESCAPING_EXCEPTION_MARKER.length());
      if (start > 0) {
        return start;
      }
    }
    i = line.indexOf(CAUSED_BY_EXCEPTION_MARKER);
    if (i >= 0) {
      return i + CAUSED_BY_EXCEPTION_MARKER.length();
    }
    i = line.indexOf(SUPPRESSED_EXCEPTION_MARKER);
    if (i >= 0) {
      return i + SUPPRESSED_EXCEPTION_MARKER.length();
    }
    return -1;
  }

  private static ExceptionLine tryParseExceptionLine(String line) {
    int start = indexOfExceptionStart(line);
    if (start < 0) {
      return null;
    }
    int end = line.indexOf(':', start);
    if (end < 0) {
      return null;
    }
    String exception = line.substring(start, end);
    return new ExceptionLine(line, start, end, Reference.classFromTypeName(exception));
  }

  private static MapInfo tryParseSourceFileMarkerForAosp(String sourceFile) {
    if (!sourceFile.startsWith(AOSP_SF_MARKER)) {
      return null;
    }
    int hashStart = AOSP_SF_MARKER.length();
    String mapHash = sourceFile.substring(hashStart);
    // Currently, app builds use the map-hash as the build id.
    return new MapInfo(mapHash, mapHash);
  }

  private static MapInfo tryParseSourceFileMarkerForR8(String sourceFile) {
    if (!sourceFile.startsWith(R8_SF_MARKER)) {
      return null;
    }
    int versionStart = R8_SF_MARKER.length();
    int mapHashStart = sourceFile.indexOf('_', versionStart) + 1;
    if (mapHashStart <= 0) {
      return null;
    }
    String version = sourceFile.substring(versionStart, mapHashStart - 1);
    String mapHash = sourceFile.substring(mapHashStart);
    return new MapInfo(version, mapHash);
  }

  private static void printIdentityStackTrace(ExceptionLine exceptionLine, List<FrameLine> frames) {
    if (exceptionLine != null) {
      System.out.println(exceptionLine.line);
    }
    frames.forEach(frame -> System.out.println(frame.line));
  }

  private static void retrace(InputStream stream, LazyRetracer defaultRetracer) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    String currentLine = reader.readLine();
    List<FrameLine> frames = new ArrayList<>();
    while (currentLine != null) {
      ExceptionLine exceptionLine = tryParseExceptionLine(currentLine);
      if (exceptionLine != null) {
        currentLine = reader.readLine();
        if (currentLine == null) {
          // Reached end-of-file and we can't retrace the exception. Flush and exit.
          printIdentityStackTrace(exceptionLine, Collections.emptyList());
          return;
        }
      }
      FrameLine topFrameLine = tryParseFrameLine(currentLine);
      if (topFrameLine == null) {
        // The line is not a frame so we can't retrace it. Flush and continue on the next line.
        printIdentityStackTrace(exceptionLine, Collections.emptyList());
        System.out.println(currentLine);
        currentLine = reader.readLine();
        continue;
      }
      // Collect all subsequent lines with the same source file info.
      FrameLine frame = topFrameLine;
      while (frame != null) {
        if (!frame.sourceFile.equals(topFrameLine.sourceFile)) {
          break;
        }
        frames.add(frame);
        currentLine = reader.readLine();
        frame = currentLine == null ? null : tryParseFrameLine(currentLine);
      }
      retraceStackTrace(defaultRetracer, exceptionLine, frames);
      frames.clear();
    }
  }

  private static LazyRetracer determineRetracer(String sourceFile, LazyRetracer defaultRetracer)
      throws Exception {
    LazyRetracer lazyRetracer = getRetracerForR8(sourceFile);
    if (lazyRetracer != null) {
      return lazyRetracer;
    }
    lazyRetracer = getRetracerForAosp(sourceFile);
    if (lazyRetracer != null) {
      return lazyRetracer;
    }
    return defaultRetracer;
  }

  private static void retraceStackTrace(
      LazyRetracer defaultRetracer, ExceptionLine exceptionLine, List<FrameLine> frames)
      throws Exception {
    String sourceFile = frames.get(0).sourceFile;
    LazyRetracer lazyRetracer = determineRetracer(sourceFile, defaultRetracer);
    if (lazyRetracer == null) {
      printIdentityStackTrace(exceptionLine, frames);
      return;
    }
    Retracer retracer = lazyRetracer.getRetracer();
    List<ResultNode> finalResultNodes = new ArrayList<>();
    retraceOptionalExceptionLine(
        retracer,
        exceptionLine,
        (context, parentResult) ->
            retraceFrameRecursive(retracer, context, parentResult, 0, frames)
                .forEach(finalResultNodes::add));

    if (finalResultNodes.size() > 1) {
      System.out.println(
          "Printing "
              + finalResultNodes.size()
              + " ambiguous stacks separated by <OR>.\n"
              + "If this is unexpected, please file a bug on R8 and attach the "
              + "content of the raw stack trace and the mapping file: "
              + lazyRetracer.mapPath
              + "\nPublic tracker at https://issuetracker.google.com/issues/new?component=326788");
    }
    for (int i = 0; i < finalResultNodes.size(); i++) {
      if (i > 0) {
        System.out.println("<OR>");
      }
      ResultNode node = finalResultNodes.get(i);
      node.print();
    }
  }

  private static void retraceOptionalExceptionLine(
      Retracer retracer,
      ExceptionLine exceptionLine,
      BiConsumer<RetraceStackTraceContext, ResultNode> resultCallback) {
    // This initial result node parent is 'null', i.e., no parent.
    ResultNode initialResultNode = null;
    if (exceptionLine == null) {
      // If no exception line is given, retracing starts in the empty context.
      resultCallback.accept(RetraceStackTraceContext.empty(), initialResultNode);
      return;
    }
    // If an exception line is given the result is possibly a forrest, so each individual result
    // has a null parent.
    retracer
        .retraceThrownException(exceptionLine.exception)
        .forEach(
            element ->
                resultCallback.accept(
                    element.getContext(),
                    new ResultNode(
                        initialResultNode,
                        exceptionLine.plug(element.getRetracedClass().getTypeName()))));
  }

  private static Stream<ResultNode> retraceFrameRecursive(
      Retracer retracer,
      RetraceStackTraceContext context,
      ResultNode parentResult,
      int frameIndex,
      List<FrameLine> frames) {
    if (frameIndex >= frames.size()) {
      return Stream.of(parentResult);
    }

    // Helper to link up frame results when iterating via a closure callback.
    class ResultLinker {
      ResultNode current;

      public ResultLinker(ResultNode current) {
        this.current = current;
      }

      public void link(String nextResult) {
        current = new ResultNode(current, nextResult);
      }
    }

    FrameLine frameLine = frames.get(frameIndex);
    return retracer
        .retraceFrame(context, frameLine.lineNumber, frameLine.clazz, frameLine.methodName)
        .flatMap(
            frameElement -> {
              // Create a linking helper to amend the result when iterating the frames.
              ResultLinker linker = new ResultLinker(parentResult);
              frameElement.forEachRewritten(
                  frame -> {
                    RetracedMethodReference method = frame.getMethodReference();
                    RetracedClassReference holder = method.getHolderClass();
                    int origPos = method.getOriginalPositionOrDefault(-1);
                    linker.link(
                        frameLine.plug(
                            holder.getTypeName()
                                + "."
                                + method.getMethodName()
                                + "("
                                + frame.getSourceFile().getOrInferSourceFile()
                                + (origPos >= 0 ? (":" + origPos) : "")
                                + ")"));
                  });
              return retraceFrameRecursive(
                  retracer,
                  frameElement.getRetraceStackTraceContext(),
                  linker.current,
                  frameIndex + 1,
                  frames);
            });
  }

  private static void populateMappingFileMap(List<String> searchPaths) throws Exception {
    Path projectRoot = getProjectRoot();
    if (projectRoot == null) {
      return;
    }
    Path prebuiltR8MapPath = projectRoot.resolve("prebuilts").resolve("r8").resolve("r8.jar.map");
    MapInfo prebuiltR8MapInfo = readMapHeaderInfo(prebuiltR8MapPath);
    if (prebuiltR8MapInfo == null) {
      info("Unable to read expected prebuilt R8 map in " + prebuiltR8MapPath);
    } else {
      RETRACERS.put(prebuiltR8MapInfo.id, new LazyRetracer(prebuiltR8MapInfo, prebuiltR8MapPath));
    }
    for (String path : searchPaths) {
      Path resolvedPath = projectRoot.resolve(Paths.get(path));
      if (Files.notExists(resolvedPath)) {
        error("Invalid search path entry: " + resolvedPath);
      }
      Files.walkFileTree(
          resolvedPath,
          new FileVisitor<Path>() {

            final Path mapFileName = Paths.get("proguard_dictionary");

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (file.endsWith(mapFileName)) {
                MapInfo mapInfo = readMapHeaderInfo(file);
                if (mapInfo != null) {
                  RETRACERS.put(mapInfo.id, new LazyRetracer(mapInfo, file));
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }

  public static void main(String[] args) throws Exception {
    String stackTraceFile = null;
    String defaultMapArg = null;
    boolean printMappingFileTable = false;
    List<String> searchPaths = AOSP_MAP_SEARCH_PATHS;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-h") || arg.equals("--help")) {
        System.out.println(USAGE);
        return;
      }
      if (arg.equals("--default-map")) {
        i++;
        if (i == args.length) {
          throw error("No argument given for --default-map");
        }
        defaultMapArg = args[i];
      } else if (arg.equals("--map-search-path")) {
        i++;
        if (i == args.length) {
          throw error("No argument given for --map-search-path");
        }
        searchPaths = parseSearchPath(args[i]);
      } else if (arg.equals("--print-map-table")) {
        printMappingFileTable = true;
      } else if (arg.startsWith("-")) {
        throw error("Unknown option: " + arg);
      } else if (stackTraceFile != null) {
        throw error("At most one input file is supported.");
      } else {
        stackTraceFile = arg;
      }
    }

    LazyRetracer defaultRetracer = null;
    if (defaultMapArg != null) {
      defaultRetracer = new LazyRetracer(null, Paths.get(defaultMapArg));
    }

    populateMappingFileMap(searchPaths);
    if (printMappingFileTable) {
      List<String> keys = new ArrayList<>(RETRACERS.keySet());
      keys.sort(String::compareTo);
      for (String key : keys) {
        LazyRetracer retracer = RETRACERS.get(key);
        System.out.println(key + " -> " + retracer.mapPath);
      }
      return;
    }

    if (stackTraceFile == null) {
      retrace(System.in, defaultRetracer);
    } else {
      Path path = Paths.get(stackTraceFile);
      if (!Files.exists(path)) {
        throw error("Input file does not exist: " + stackTraceFile);
      }
      try (InputStream stream = Files.newInputStream(path, StandardOpenOption.READ)) {
        retrace(stream, defaultRetracer);
      }
    }
    flushPendingMessages();
  }

  private static List<String> parseSearchPath(String paths) {
    int length = paths.length();
    List<String> result = new ArrayList<>();
    int start = 0;
    do {
      int split = paths.indexOf(':', start);
      int end = split != -1 ? split : length;
      String path = paths.substring(start, end).strip();
      if (!path.isEmpty()) {
        result.add(path);
      }
      start = end + 1;
    } while (start < length);
    return result;
  }
}
