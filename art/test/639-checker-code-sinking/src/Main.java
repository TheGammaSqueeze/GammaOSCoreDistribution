/*
 * Copyright (C) 2017 The Android Open Source Project
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

public class Main {

  public static void main(String[] args) {
    testSimpleUse();
    testTwoUses();
    testFieldStores(doThrow);
    testFieldStoreCycle();
    testArrayStores();
    testOnlyStoreUses();
    testNoUse();
    testPhiInput();
    testVolatileStore();
    testCatchBlock();
    doThrow = true;
    try {
      testInstanceSideEffects();
    } catch (Error e) {
      // expected
      System.out.println(e.getMessage());
    }
    try {
      testStaticSideEffects();
    } catch (Error e) {
      // expected
      System.out.println(e.getMessage());
    }

    try {
      testStoreStore(doThrow);
    } catch (Error e) {
      // expected
      System.out.println(e.getMessage());
    }
  }

  /// CHECK-START: void Main.testSimpleUse() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK: <<New:l\d+>>       NewInstance [<<LoadClass>>]
  /// CHECK:                    ConstructorFence [<<New>>]
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK:                    Throw

  /// CHECK-START: void Main.testSimpleUse() code_sinking (after)
  /// CHECK-NOT:                NewInstance
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK: <<Error:l\d+>>     LoadClass class_name:java.lang.Error
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK-NOT:                begin_block
  /// CHECK: <<New:l\d+>>       NewInstance [<<LoadClass>>]
  /// CHECK:                    ConstructorFence [<<New>>]
  /// CHECK-NOT:                begin_block
  /// CHECK:                    NewInstance [<<Error>>]
  /// CHECK:                    Throw
  public static void testSimpleUse() {
    Object o = new Object();
    if (doThrow) {
      throw new Error(o.toString());
    }
  }

  /// CHECK-START: void Main.testTwoUses() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK:                    NewInstance [<<LoadClass>>]
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK:                    Throw

  /// CHECK-START: void Main.testTwoUses() code_sinking (after)
  /// CHECK-NOT:                NewInstance
  /// CHECK:                    If
  /// CHECK:                    begin_block
  /// CHECK: <<Error:l\d+>>     LoadClass class_name:java.lang.Error
  /// CHECK: <<LoadClass:l\d+>> LoadClass class_name:java.lang.Object
  /// CHECK-NOT:                begin_block
  /// CHECK:                    NewInstance [<<LoadClass>>]
  /// CHECK-NOT:                begin_block
  /// CHECK:                    NewInstance [<<Error>>]
  /// CHECK:                    Throw
  public static void testTwoUses() {
    Object o = new Object();
    if (doThrow) {
      throw new Error(o.toString() + o.toString());
    }
  }

  // NB It might seem that we'd move the allocation and ifield-set but those are
  // already moved into the throw block by a combo of partial-LSE and DCE.
  // Instead all that is actually moved is the LoadClass. Also note the
  // LoadClass can only be moved since it refers to the 'Main' class itself,
  // meaning there's no need for any clinit/actual loading.
  //
  /// CHECK-START: void Main.testFieldStores(boolean) code_sinking (before)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK:                      begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                      InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testFieldStores(boolean) code_sinking (after)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK-NOT:                  NewInstance
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<Throw:l\d+>>       NewInstance [<<Error>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      Throw [<<Throw>>]
  public static void testFieldStores(boolean doThrow) {
    Main m = new Main();
    m.intField = 42;
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  /// CHECK-START: void Main.testFieldStoreCycle() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance1:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK: <<NewInstance2:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance1>>,<<NewInstance2>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance2>>,<<NewInstance1>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw

  // TODO(ngeoffray): Handle allocation/store cycles.
  /// CHECK-START: void Main.testFieldStoreCycle() code_sinking (after)
  /// CHECK: begin_block
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance1:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK: <<NewInstance2:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance1>>,<<NewInstance2>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance2>>,<<NewInstance1>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw
  public static void testFieldStoreCycle() {
    Main m1 = new Main();
    Main m2 = new Main();
    m1.objectField = m2;
    m2.objectField = m1;
    if (doThrow) {
      throw new Error(m1.toString() + m2.toString());
    }
  }

  /// CHECK-START: void Main.testArrayStores() code_sinking (before)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK: <<NewArray:l\d+>>    NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK:                      ArraySet [<<NewArray>>,<<Int0>>,<<NewArray>>]
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testArrayStores() code_sinking (after)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK-NOT:                  NewArray
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<NewArray:l\d+>>    NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      ArraySet [<<NewArray>>,<<Int0>>,<<NewArray>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      NewInstance [<<Error>>]
  /// CHECK:                      Throw
  public static void testArrayStores() {
    Object[] o = new Object[1];
    o[0] = o;
    if (doThrow) {
      throw new Error(o.toString());
    }
  }

  // Make sure code sinking does not crash on dead allocations.
  public static void testOnlyStoreUses() {
    Main m = new Main();
    Object[] o = new Object[1];  // dead allocation, should eventually be removed b/35634932.
    o[0] = m;
    o = null;  // Avoid environment uses for the array allocation.
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  // Make sure code sinking does not crash on dead code.
  public static void testNoUse() {
    Main m = new Main();
    boolean load = Main.doLoop;  // dead code, not removed because of environment use.
    // Ensure one environment use for the static field
    $opt$noinline$foo();
    load = false;
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  // Make sure we can move code only used by a phi.
  /// CHECK-START: void Main.testPhiInput() code_sinking (before)
  /// CHECK: <<Null:l\d+>>        NullConstant
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK:                      Phi [<<Null>>,<<NewInstance>>]
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testPhiInput() code_sinking (after)
  /// CHECK: <<Null:l\d+>>        NullConstant
  /// CHECK-NOT:                  NewInstance
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK:                      begin_block
  /// CHECK:                      Phi [<<Null>>,<<NewInstance>>]
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK:                      NewInstance [<<Error>>]
  /// CHECK:                      Throw
  public static void testPhiInput() {
    Object f = new Object();
    if (doThrow) {
      Object o = null;
      int i = 2;
      if (doLoop) {
        o = f;
        i = 42;
      }
      throw new Error(o.toString() + i);
    }
  }

  static void $opt$noinline$foo() {}

  // Check that we do not move volatile stores.
  /// CHECK-START: void Main.testVolatileStore() code_sinking (before)
  /// CHECK: <<Int42:i\d+>>        IntConstant 42
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>>  NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw

  /// CHECK-START: void Main.testVolatileStore() code_sinking (after)
  /// CHECK: <<Int42:i\d+>>        IntConstant 42
  /// CHECK: <<LoadClass:l\d+>>    LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>>  NewInstance [<<LoadClass>>]
  /// CHECK:                       InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK:                       If
  /// CHECK:                       begin_block
  /// CHECK:                       Throw
  public static void testVolatileStore() {
    Main m = new Main();
    m.volatileField = 42;
    if (doThrow) {
      throw new Error(m.toString());
    }
  }

  public static void testInstanceSideEffects() {
    int a = mainField.intField;
    $noinline$changeIntField();
    if (doThrow) {
      throw new Error("" + a);
    }
  }

  static void $noinline$changeIntField() {
    mainField.intField = 42;
  }

  public static void testStaticSideEffects() {
    Object o = obj;
    $noinline$changeStaticObjectField();
    if (doThrow) {
      throw new Error(o.getClass().toString());
    }
  }

  static void $noinline$changeStaticObjectField() {
    obj = new Main();
  }

  // Test that we preserve the order of stores.
  // NB It might seem that we'd move the allocation and ifield-set but those are
  // already moved into the throw block by a combo of partial-LSE and DCE.
  // Instead all that is actually moved is the LoadClass. Also note the
  // LoadClass can only be moved since it refers to the 'Main' class itself,
  // meaning there's no need for any clinit/actual loading.
  //
  /// CHECK-START: void Main.testStoreStore(boolean) code_sinking (before)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK: <<Int43:i\d+>>       IntConstant 43
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int43>>]
  /// CHECK:                      Throw
  /// CHECK-NOT:                  InstanceFieldSet

  /// CHECK-START: void Main.testStoreStore(boolean) code_sinking (after)
  /// CHECK: <<Int42:i\d+>>       IntConstant 42
  /// CHECK: <<Int43:i\d+>>       IntConstant 43
  /// CHECK-NOT:                  NewInstance
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<Error:l\d+>>       LoadClass class_name:java.lang.Error
  /// CHECK-NOT:                  begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:Main
  /// CHECK: <<NewInstance:l\d+>> NewInstance [<<LoadClass>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int42>>]
  /// CHECK-DAG:                  InstanceFieldSet [<<NewInstance>>,<<Int43>>]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      NewInstance [<<Error>>]
  /// CHECK:                      Throw
  /// CHECK-NOT:                  InstanceFieldSet
  public static void testStoreStore(boolean doThrow) {
    Main m = new Main();
    m.intField = 42;
    m.intField2 = 43;
    if (doThrow) {
      throw new Error(m.$opt$noinline$toString());
    }
  }

  static native void doStaticNativeCallLiveVreg();

  //  Test ensures that 'o' has been moved into the if despite the InvokeStaticOrDirect.
  //
  /// CHECK-START: void Main.testSinkingOverInvoke() code_sinking (before)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK-NOT:                  begin_block
  /// CHECK:                      NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK:                      Throw

  /// CHECK-START: void Main.testSinkingOverInvoke() code_sinking (after)
  /// CHECK: <<Int1:i\d+>>        IntConstant 1
  /// CHECK: <<Int0:i\d+>>        IntConstant 0
  /// CHECK:                      If
  /// CHECK:                      begin_block
  /// CHECK: <<LoadClass:l\d+>>   LoadClass class_name:java.lang.Object[]
  /// CHECK:                      NewArray [<<LoadClass>>,<<Int1>>]
  /// CHECK:                      Throw
  static void testSinkingOverInvoke() {
    Object[] o = new Object[1];
    o[0] = o;
    doStaticNativeCallLiveVreg();
    if (doThrow) {
      throw new Error(o.toString());
    }
  }

  public String $opt$noinline$toString() {
    return "" + intField;
  }

  private static void testCatchBlock() {
    assertEquals(456, testSinkToCatchBlock());
    assertEquals(456, testDoNotSinkToTry());
    assertEquals(456, testDoNotSinkToCatchInsideTry());
    assertEquals(456, testSinkWithinTryBlock());
    assertEquals(456, testSinkRightBeforeTryBlock());
    assertEquals(456, testSinkToSecondCatch());
    assertEquals(456, testDoNotSinkToCatchInsideTryWithMoreThings(false, false));
    assertEquals(456, testSinkToCatchBlockCustomClass());
    assertEquals(456, DoNotSinkWithOOMThrow());
  }

  /// CHECK-START: int Main.testSinkToCatchBlock() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testSinkToCatchBlock() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]

  // Consistency check to make sure there's only one entry TryBoundary.
  /// CHECK-START: int Main.testSinkToCatchBlock() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry

  // Tests that we can sink the Object creation to the catch block.
  private static int testSinkToCatchBlock() {
    Object o = new Object();
    try {
      if (doEarlyReturn) {
        return 123;
      }
    } catch (Error e) {
      throw new Error(o.toString());
    }
    return 456;
  }

  /// CHECK-START: int Main.testDoNotSinkToTry() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testDoNotSinkToTry() code_sinking (after)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry

  // Consistency check to make sure there's only one entry TryBoundary.
  /// CHECK-START: int Main.testDoNotSinkToTry() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry

  // Tests that we don't sink the Object creation into the try.
  private static int testDoNotSinkToTry() {
    Object o = new Object();
    try {
      if (doEarlyReturn) {
        throw new Error(o.toString());
      }
    } catch (Error e) {
      throw new Error();
    }
    return 456;
  }

  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTry() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTry() code_sinking (after)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         TryBoundary kind:entry

  // Consistency check to make sure there's exactly two entry TryBoundary.
  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTry() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry

  // Tests that we don't sink the Object creation into a catch handler surrounded by try/catch.
  private static int testDoNotSinkToCatchInsideTry() {
    Object o = new Object();
    try {
      try {
        if (doEarlyReturn) {
          return 123;
        }
      } catch (Error e) {
        throw new Error(o.toString());
      }
    } catch (Error e) {
      throw new Error();
    }
    return 456;
  }

  /// CHECK-START: int Main.testSinkWithinTryBlock() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         If

  /// CHECK-START: int Main.testSinkWithinTryBlock() code_sinking (after)
  /// CHECK:                         If
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  private static int testSinkWithinTryBlock() {
    try {
      Object o = new Object();
      if (doEarlyReturn) {
        throw new Error(o.toString());
      }
    } catch (Error e) {
      return 123;
    }
    return 456;
  }

  /// CHECK-START: int Main.testSinkRightBeforeTryBlock() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         If
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testSinkRightBeforeTryBlock() code_sinking (after)
  /// CHECK:                         If
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry
  private static int testSinkRightBeforeTryBlock() {
    Object o = new Object();
    if (doEarlyReturn) {
      try {
        throw new Error(o.toString());
      } catch (Error e) {
        return 123;
      }
    }
    return 456;
  }

  /// CHECK-START: int Main.testSinkToSecondCatch() code_sinking (before)
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testSinkToSecondCatch() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]

  // Consistency check to make sure there's exactly two entry TryBoundary.
  /// CHECK-START: int Main.testSinkToSecondCatch() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry
  private static int testSinkToSecondCatch() {
    Object o = new Object();
    try {
      if (doEarlyReturn) {
        return 123;
      }
    } catch (Error e) {
      throw new Error();
    }

    try {
      // We need a different boolean to the one above, so that the compiler cannot optimize this
      // return away.
      if (doOtherEarlyReturn) {
        return 789;
      }
    } catch (Error e) {
      throw new Error(o.toString());
    }

    return 456;
  }

  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTryWithMoreThings(boolean, boolean) code_sinking (before)
  /// CHECK-NOT:                     TryBoundary kind:entry
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]

  /// CHECK-START: int Main.testDoNotSinkToCatchInsideTryWithMoreThings(boolean, boolean) code_sinking (after)
  /// CHECK-NOT:                     TryBoundary kind:entry
  /// CHECK: <<ObjLoadClass:l\d+>>   LoadClass class_name:java.lang.Object
  /// CHECK:                         NewInstance [<<ObjLoadClass>>]

  // Tests that we don't sink the Object creation into a catch handler surrounded by try/catch, even
  // when that inner catch is not at the boundary of the outer try catch.
  private static int testDoNotSinkToCatchInsideTryWithMoreThings(boolean a, boolean b) {
    Object o = new Object();
    try {
      if (a) {
        System.out.println(a);
      }
      try {
        if (doEarlyReturn) {
          return 123;
        }
      } catch (Error e) {
        throw new Error(o.toString());
      }
      if (b) {
        System.out.println(b);
      }
    } catch (Error e) {
      throw new Error();
    }
    return 456;
  }

  private static class ObjectWithInt {
    int x;
  }

  /// CHECK-START: int Main.testSinkToCatchBlockCustomClass() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>>      LoadClass class_name:Main$ObjectWithInt
  /// CHECK: <<Clinit:l\d+>>         ClinitCheck [<<LoadClass>>]
  /// CHECK:                         NewInstance [<<Clinit>>]
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.testSinkToCatchBlockCustomClass() code_sinking (after)
  /// CHECK: <<LoadClass:l\d+>>      LoadClass class_name:Main$ObjectWithInt
  /// CHECK: <<Clinit:l\d+>>         ClinitCheck [<<LoadClass>>]
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK:                         NewInstance [<<Clinit>>]

  // Consistency check to make sure there's only one entry TryBoundary.
  /// CHECK-START: int Main.testSinkToCatchBlockCustomClass() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry

  // Similar to testSinkToCatchBlock, but using a custom class. CLinit check is not an instruction
  // that we sink since it can throw and it is not in the allow list. We can sink the NewInstance
  // nevertheless.
  private static int testSinkToCatchBlockCustomClass() {
    ObjectWithInt obj = new ObjectWithInt();
    try {
      if (doEarlyReturn) {
        return 123;
      }
    } catch (Error e) {
      throw new Error(Integer.toString(obj.x));
    }
    return 456;
  }

  /// CHECK-START: int Main.DoNotSinkWithOOMThrow() code_sinking (before)
  /// CHECK: <<LoadClass:l\d+>>      LoadClass class_name:Main$ObjectWithInt
  /// CHECK: <<Clinit:l\d+>>         ClinitCheck [<<LoadClass>>]
  /// CHECK:                         NewInstance [<<Clinit>>]
  /// CHECK:                         TryBoundary kind:entry

  /// CHECK-START: int Main.DoNotSinkWithOOMThrow() code_sinking (after)
  /// CHECK: <<LoadClass:l\d+>>      LoadClass class_name:Main$ObjectWithInt
  /// CHECK: <<Clinit:l\d+>>         ClinitCheck [<<LoadClass>>]
  /// CHECK:                         NewInstance [<<Clinit>>]
  /// CHECK:                         TryBoundary kind:entry

  // Consistency check to make sure there's only one entry TryBoundary.
  /// CHECK-START: int Main.DoNotSinkWithOOMThrow() code_sinking (after)
  /// CHECK:                         TryBoundary kind:entry
  /// CHECK-NOT:                     TryBoundary kind:entry
  private static int DoNotSinkWithOOMThrow() throws OutOfMemoryError {
    int x = 0;
    ObjectWithInt obj = new ObjectWithInt();
    try {
      // We want an if/else here so that the catch block will have a catch phi.
      if (doThrow) {
        x = 1;
        // Doesn't really matter what we throw we just want it to not be caught by the
        // NullPointerException below.
        throw new OutOfMemoryError(Integer.toString(obj.x));
      } else {
        x = 456;
      }
    } catch (NullPointerException e) {
    }

    // We want to use obj over here so that it doesn't get deleted by LSE.
    if (obj.x == 123) {
      return 123;
    }
    return x;
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }
  }

  volatile int volatileField;
  int intField;
  int intField2;
  Object objectField;
  static boolean doThrow;
  static boolean doLoop;
  static boolean doEarlyReturn;
  static boolean doOtherEarlyReturn;
  static Main mainField = new Main();
  static Object obj = new Object();
}
