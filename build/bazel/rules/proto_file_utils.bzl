"""
Copyright (C) 2021 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("@bazel_skylib//lib:paths.bzl", "paths")

def _generate_and_declare_output_files(
        ctx,
        file_names,
        type_dictionary):
    ret = {}
    for typ in type_dictionary:
        ret[typ] = []

    for name in file_names:
        short_path = name.short_path
        for typ, ext in type_dictionary.items():
            # prefix with label.name to prevent collisions between targets
            # if proto compliation becomes an aspect, can prefix with output
            # information instead to allow reuse, e.g. multiple cc `lite`
            # libraries containing the same proto file
            out_name = paths.join(ctx.label.name, paths.replace_extension(short_path, ext))
            declared = ctx.actions.declare_file(out_name)
            ret[typ].append(declared)

    return ret

def _generate_jar_proto_action(
        proto_info,
        protoc,
        ctx,
        out_flags = [],
        plugin_executable = None,
        out_arg = None,
        mnemonic = "ProtoGen"):

    jar_basename = ctx.label.name + "-proto_gen"
    jar_name = jar_basename + "-src.jar"
    jar_file = ctx.actions.declare_file(jar_name)

    _generate_proto_action(
        proto_info = proto_info,
        protoc = protoc,
        ctx = ctx,
        out_flags = out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        mnemonic = mnemonic,
        output_file = jar_file,
    )

    srcjar_name = jar_basename + ".srcjar"
    srcjar_file = ctx.actions.declare_file(srcjar_name)
    ctx.actions.symlink(
        output = srcjar_file,
        target_file = jar_file,
    )

    return srcjar_file

def _generate_proto_action(
        proto_info,
        protoc,
        ctx,
        type_dictionary = None,
        out_flags = [],
        plugin_executable = None,
        out_arg = None,
        mnemonic = "ProtoGen",
        output_file = None):
    """ Utility function for creating proto_compiler action.

    Args:
      proto_info: ProtoInfo
      protoc: proto compiler executable.
      ctx: context, used for declaring new files only.
      type_dictionary: a dictionary of types to output extensions
      out_flags: protoc output flags
      plugin_executable: plugin executable file
      out_arg: as appropriate, if plugin_executable and out_arg are both supplied, plugin_executable is preferred
      mnemonic: (optional) a string to describe the proto compilation action
      output_file: (optional) File, used to specify a specific file for protoc output (typically a JAR file)

    Returns:
      Dictionary with declared files grouped by type from the type_dictionary.
    """
    proto_srcs = proto_info.direct_sources
    transitive_proto_srcs = proto_info.transitive_imports

    protoc_out_name = paths.join(ctx.bin_dir.path, ctx.label.package)
    if output_file:
        protoc_out_name = paths.join(protoc_out_name, output_file.basename)
        out_files = {
            "out": [output_file]
        }
    else:
        protoc_out_name = paths.join(protoc_out_name, ctx.label.name)
        out_files = _generate_and_declare_output_files(
            ctx,
            proto_srcs,
            type_dictionary,
        )

    tools = []
    args = ctx.actions.args()
    if plugin_executable:
        tools.append(plugin_executable)
        args.add("--plugin=protoc-gen-PLUGIN=" + plugin_executable.path)
        args.add("--PLUGIN_out=" + ",".join(out_flags) + ":" + protoc_out_name)
    else:
        args.add("{}={}:{}".format(out_arg, ",".join(out_flags), protoc_out_name))

    args.add_all(["-I", proto_info.proto_source_root])
    args.add_all(["-I{0}={1}".format(f.short_path, f.path) for f in transitive_proto_srcs.to_list()])
    args.add_all([f.short_path for f in proto_srcs])

    inputs = depset(
        direct = proto_srcs,
        transitive = [transitive_proto_srcs],
    )

    outputs = []
    for outs in out_files.values():
        outputs.extend(outs)

    ctx.actions.run(
        inputs = inputs,
        executable = protoc,
        tools = tools,
        outputs = outputs,
        arguments = [args],
        mnemonic = mnemonic,
    )
    return out_files

proto_file_utils = struct(
    generate_proto_action = _generate_proto_action,
    generate_jar_proto_action = _generate_jar_proto_action,
)
