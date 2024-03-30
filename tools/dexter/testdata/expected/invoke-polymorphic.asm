
method Main.getHandleForStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
{
	    0| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	    3| move-result-object v0
	    4| invoke-virtual {v0,v2,v3,v4}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	    7| move-result-object v1
	    8| return-object v1
}

method Main.getHandleForVirtual(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
{
	    0| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	    3| move-result-object v0
	    4| invoke-virtual {v0,v2,v3,v4}, java.lang.invoke.MethodHandles$Lookup.findVirtual(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	    7| move-result-object v1
	    8| return-object v1
}

method Main.getLongCompareToHandle():java.lang.invoke.MethodHandle
{
	    0| new-instance v0, java.lang.Long
	    2| const-wide v1:v2, #+0 (0x0000000000000000 | 0.00000)
	    7| invoke-direct {v0,v1,v2}, java.lang.Long.<init>(long):void
	   10| invoke-virtual {v0}, java.lang.Object.getClass():java.lang.Class
	   13| move-result-object v0
	   14| sget-object v1, java.lang.Integer.TYPE
	   16| invoke-static {v1,v0}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class):java.lang.invoke.MethodType
	   19| move-result-object v2
	   20| const-string v3, "compareTo"
	   22| invoke-static {v0,v3,v2}, Main.getHandleForVirtual(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	   25| move-result-object v0
	   26| return-object v0
}

method Main.getStringConcatHandle():java.lang.invoke.MethodHandle
{
	    0| const-string v0, "concat"
	    2| invoke-virtual {v0}, java.lang.Object.getClass():java.lang.Class
	    5| move-result-object v1
	    6| invoke-static {v1,v1}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class):java.lang.invoke.MethodType
	    9| move-result-object v2
	   10| invoke-static {v1,v0,v2}, Main.getHandleForVirtual(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	   13| move-result-object v0
	   14| return-object v0
}

method Main.getStringValueOfLongHandle():java.lang.invoke.MethodHandle
{
	    0| sget-object v0, java.lang.Long.TYPE
	    2| const-string v1, "valueOf"
	    4| invoke-virtual {v1}, java.lang.Object.getClass():java.lang.Class
	    7| move-result-object v2
	    8| invoke-static {v2,v0}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class):java.lang.invoke.MethodType
	   11| move-result-object v3
	   12| invoke-static {v2,v1,v3}, Main.getHandleForStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	   15| move-result-object v0
	   16| return-object v0
}

method Main.getStringValueOfObjectHandle():java.lang.invoke.MethodHandle
{
	    0| new-instance v0, java.lang.Object
	    2| invoke-direct {v0}, java.lang.Object.<init>():void
	    5| invoke-virtual {v0}, java.lang.Object.getClass():java.lang.Class
	    8| move-result-object v0
	    9| const-string v1, "valueOf"
	   11| invoke-virtual {v1}, java.lang.Object.getClass():java.lang.Class
	   14| move-result-object v2
	   15| invoke-static {v2,v0}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class):java.lang.invoke.MethodType
	   18| move-result-object v3
	   19| invoke-static {v2,v1,v3}, Main.getHandleForStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	   22| move-result-object v0
	   23| return-object v0
}

method Main.main(java.lang.String[]):void
{
	    0| invoke-static {}, Main.getStringConcatHandle():java.lang.invoke.MethodHandle
	    3| move-result-object v0
	    4| const-string v1, "[String1]"
	    6| const-string v2, "+[String2]"
	    8| invoke-polymorphic {v0,v1,v2}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (java.lang.String, java.lang.String):java.lang.String
	   12| move-result-object v3
	   13| sget-object v4, java.lang.System.out
	   15| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	   18| invoke-static {}, Main.getStringValueOfObjectHandle():java.lang.invoke.MethodHandle
	   21| move-result-object v0
	   22| const-string v1, "[String1]"
	   24| invoke-polymorphic {v0,v1}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (java.lang.Object):java.lang.String
	   28| move-result-object v3
	   29| sget-object v4, java.lang.System.out
	   31| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	   34| invoke-static {}, Main.getStringConcatHandle():java.lang.invoke.MethodHandle
	   37| move-result-object v0
	   38| const-string v1, "[String1]"
	   40| const-string v2, "+[String2]"
	   42| invoke-polymorphic {v0,v1,v2}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Object, java.lang.Object):java.lang.String
	   46| move-result-object v3
	   47| sget-object v4, java.lang.System.out
	   49| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	   52| invoke-static {}, Main.getStringValueOfLongHandle():java.lang.invoke.MethodHandle
	   55| move-result-object v0
	   56| const-wide v1:v2, #+42 (0x000000000000002a | 2.07508e-322)
	   61| invoke-polymorphic {v0,v1,v2}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (long):java.lang.String
	   65| move-result-object v3
	   66| sget-object v4, java.lang.System.out
	   68| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	   71| const v1, #+40 (0x00000028 | 5.60519e-44)
	   74| invoke-polymorphic {v0,v1}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (int):java.lang.String
	   78| move-result-object v3
	   79| sget-object v4, java.lang.System.out
	   81| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	   84| new-instance v1, java.lang.Long
	   86| const-wide v2:v3, #+43 (0x000000000000002b | 2.12448e-322)
	   91| invoke-direct {v1,v2,v3}, java.lang.Long.<init>(long):void
	   94| invoke-polymorphic {v0,v1}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Long):java.lang.String
	   98| move-result-object v3
	   99| sget-object v4, java.lang.System.out
	  101| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	  104| new-instance v1, java.lang.Integer
	  106| const v2, #+44 (0x0000002c | 6.16571e-44)
	  109| invoke-direct {v1,v2}, java.lang.Integer.<init>(int):void
	  112| invoke-polymorphic {v0,v1}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Integer):java.lang.String
	  116| move-result-object v3
	  117| sget-object v4, java.lang.System.out
	  119| invoke-virtual {v4,v3}, java.io.PrintStream.println(java.lang.String):void
	  122| invoke-static {}, Main.getLongCompareToHandle():java.lang.invoke.MethodHandle
	  125| move-result-object v0
	  126| new-instance v1, java.lang.Long
	  128| const-wide v2:v3, #+43 (0x000000000000002b | 2.12448e-322)
	  133| invoke-direct {v1,v2,v3}, java.lang.Long.<init>(long):void
	  136| invoke-polymorphic {v0,v1,v1}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Long, java.lang.Long):int
	  140| move-result v3
	  141| sget-object v4, java.lang.System.out
	  143| invoke-virtual {v4,v3}, java.io.PrintStream.println(int):void
	  146| const-wide v2:v3, #+44 (0x000000000000002c | 2.17389e-322)
	  151| invoke-polymorphic {v0,v1,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Long, long):int
	  155| move-result v3
	  156| sget-object v4, java.lang.System.out
	  158| invoke-virtual {v4,v3}, java.io.PrintStream.println(int):void
	  161| invoke-polymorphic/range {v7..v12}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Long, long):int
	  165| invoke-polymorphic/range {v3..v43}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (java.lang.Long, long):int
	  169| return-void
}
