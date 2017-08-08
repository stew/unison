package org.unisonweb.codegeneration

import java.io.File

object DynamicCallGenerator {
  def apply(outDir: File): (File, String) =
    (new File(outDir, "DynamicCall.scala"), source)

  val N = maxInlineArity
  def source =

   "package org.unisonweb" <>
   "package compilation" <>
   "" <>
   "import Runtime._" <>
   "import Term.{Term,Name}" <>
   "import annotation.switch" <>
   "" <>
   "object DynamicCall " + {
     "" <>
     "def dynamicCall(fn: Rt, args: Array[Rt], decompile: Term, isTail: Boolean): Rt = " <>
     "  if (isTail) dynamicTailCall(fn, args, decompile)" <>
     "  else dynamicNonTailCall(fn, args, decompile)" <>
     "" <>
     sourceDynamicCall(isTail = false) <>
     "" <>
     sourceDynamicCall(isTail = true)
   }.b

  def emptyOrNon(isTail: Boolean) = if (isTail) "" else "Non"

  def sourceDynamicCall(isTail: Boolean): String =
  s"def dynamic${emptyOrNon(isTail)}TailCall(fn: Rt, args: Array[Rt], decompile: Term): Rt = " + {
     "val arity = args.map(_.arity).max" <>
     "val args2 = args" <>
     "(arity: @switch) match " + { (0 to N).each { i =>
        s"case $i => (args.length: @switch) match " + { (1 to N).each { j =>
          s"case $j => " <> {
            { s"new Arity$i(decompile) " + {
               (0 until j).each(j => s"val arg$j = args2($j)") <>
               "def bind(env: Map[Name,Rt]) = args2.foreach(_.bind(env))" <>
               applySignature(i) + " = " + {
                 { "val fn2 = " + evalBoxed(i, "fn") } <>
                 (0 until j).each { j =>
                   s"val arg${j}r = " + eval(i, s"arg$j") + "; " +
                   s"val arg${j}rb = r.boxed"
                 } <>
                 // fn2(fn2, arg1r, arg1br, arg0r, arg0rb, r)
                 (if (!isTail)
                   s"fn2(fn2, " + ((j-1) to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)"
                 else
                   s"tailCall(fn2, " + ((j-1) to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)"
                 )
               }.b
            }.b }
          }.indent } nl
           s"case j => new Arity$i(decompile) " + {
              "def bind(env: Map[Name,Rt]) = args2.foreach(_.bind(env))" <>
              applySignature(i) + " = " + {
                "val argsr = new Array[Slot](args2.length)" <>
                { "val fn2 = " + evalBoxed(i, "fn") } <>
                "var k = 0" <>
                "while (k < argsr.length) " + {
                   "argsr(k) = new Slot(" + eval(i, "args2(k)") + ", r.boxed)" <>
                   "k += 1"
                }.b <>
                (if (!isTail) "fn2(fn2, argsr.reverse, r)"
                 else "tailCall(fn2, argsr.reverse, r)")
              }.b
           }.b
        }.b } nl
        "case n => (args.length: @switch) match " + { (1 to N).each { j =>
          s"case $j => new ArityN(n, decompile) " + {
            (0 until j).each(j => s"val arg$j = args($j)") <>
            "def bind(env: Map[Name,Rt]) = args2.foreach(_.bind(env))" <>
            "def apply(rec: Rt, xs: Array[Slot], r: R) = " + {
              "val fn2 = { " + evalN("fn") + "; r.boxed } " <>
              (0 until j).each { j =>
                s"val arg${j}r = " + evalN(s"arg$j") + "; " +
                s"val arg${j}rb = r.boxed"
              } <>
              (if (!isTail)
                s"fn2(fn2, " + ((j-1) to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)"
              else
                s"tailCall(fn2, " + ((j-1) to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)"
              )
            }.b
          }.b
        } nl "case j => new ArityN(n, decompile) " + {
          "def bind(env: Map[Name,Rt]) = args2.foreach(_.bind(env))" <>
          "def apply(rec: Rt, xs: Array[Slot], r: R) = " + {
             "val argsr = new Array[Slot](args2.length)" <>
             ("val fn2 = { " + evalN("fn") + "; r.boxed }") <>
             "var k = 0" <>
             "while (k < argsr.length) " + {
                "argsr(k) = new Slot(" + evalN("args2(k)") + ", r.boxed)" <>
                "k += 1"
              }.b <>
              (if (!isTail) "fn2(fn2, argsr.reverse, r)"
               else "tailCall(fn2, argsr.reverse, r)")
          }.b
        }.b
        }.b
      }.b
   }.b
}
