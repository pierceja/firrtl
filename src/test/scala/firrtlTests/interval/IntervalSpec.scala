package firrtlTests
package interval

import java.io._
import firrtl._
import firrtl.ir.Circuit
import firrtl.passes._
import firrtl.Parser.IgnoreInfo

class IntervalSpec extends FirrtlFlatSpec {
  private def executeTest(input: String, expected: Seq[String], passes: Seq[Pass]) = {
    val c = passes.foldLeft(Parser.parse(input.split("\n").toIterator)) {
      (c: Circuit, p: Pass) => p.run(c)
    }
    val lines = c.serialize.split("\n") map normalized

    expected foreach { e =>
      lines should contain(e)
    }
  }

  "Interval types" should "parse correctly" in {
    val passes = Seq(ToWorkingIR)
    val input =
      """circuit Unit :
        |  module Unit :
        |    input in0 : Interval(-0.32, 10.1).4
        |    input in1 : Interval[0, 10.1].4
        |    input in2 : Interval(-0.32, 10].4
        |    input in3 : Interval[-3, 10.1).4
        |    input in4 : Interval(-0.32, 10.1)
        |    input in5 : Interval.4
        |    input in6 : Interval
        |    output out0 : Interval.2
        |    output out1 : Interval
        |    out0 <= add(in0, add(in1, add(in2, add(in3, add(in4, add(in5, in6))))))
        |    out1 <= add(in0, add(in1, add(in2, add(in3, add(in4, add(in5, in6))))))""".stripMargin
    executeTest(input, input.split("\n") map normalized, passes)
  }

  "Interval types" should "infer bp correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
    val input =
      """circuit Unit :
        |  module Unit :
        |    input in0 : Interval(-0.32, 10.1).4
        |    input in1 : Interval[0, 10.1].3
        |    input in2 : Interval(-0.32, 10].2
        |    output out0 : Interval
        |    out0 <= add(in0, add(in1, in2))""".stripMargin
    val check =
      """circuit Unit :
        |  module Unit :
        |    input in0 : Interval(-0.32, 10.1).4
        |    input in1 : Interval[0, 10.1].3
        |    input in2 : Interval(-0.32, 10].2
        |    output out0 : Interval(-0.64, 30.2).4
        |    out0 <= add(in0, add(in1, in2))""".stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }

  "Interval types" should "infer intervals correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
    val input =
      """circuit Unit :
        |  module Unit :
        |    input in0 : Interval(0, 10).4
        |    input in1 : Interval(0, 10].3
        |    input in2 : Interval(-1, 3].2
        |    output out0 : Interval
        |    output out1 : Interval
        |    output out2 : Interval
        |    out0 <= add(in0, add(in1, in2))
        |    out1 <= mul(in0, mul(in1, in2))
        |    out2 <= sub(in0, sub(in1, in2))""".stripMargin
    val check =
      """output out0 : Interval(-1, 23).4
        |output out1 : Interval(-100, 300).9
        |output out2 : Interval(-11, 13).4""".stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }

  "Interval types" should "be removed correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths(), new RemoveIntervals())
    val input =
      """circuit Unit :
        |  module Unit :
        |    input in0 : Interval(0, 10).4
        |    input in1 : Interval(0, 10].3
        |    input in2 : Interval(-1, 3].2
        |    output out0 : Interval
        |    output out1 : Interval
        |    output out2 : Interval
        |    out0 <= add(in0, add(in1, in2))
        |    out1 <= mul(in0, mul(in1, in2))
        |    out2 <= sub(in0, sub(in1, in2))""".stripMargin
    val check =
      """circuit Unit :
        |  module Unit :
        |    input in0 : SInt<9>
        |    input in1 : SInt<8>
        |    input in2 : SInt<5>
        |    output out0 : SInt<10>
        |    output out1 : SInt<19>
        |    output out2 : SInt<9>
        |    out0 <= add(in0, shl(add(in1, shl(in2, 1)), 1))
        |    out1 <= mul(in0, mul(in1, in2))
        |    out2 <= sub(in0, shl(sub(in1, shl(in2, 1)), 1))""".stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }

  "Interval types" should "infer this example correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  in1 : Interval(0.5, 0.5].1
        |    input  in2 : Interval(-0.5, 0.5).1
        |    output sum : Interval
        |    sum <= add(in2, in1)
        |    """.stripMargin
    val check = s"""output sum : Interval(0, 1).1 """.stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }

  "Interval types" should "infer multiplication by zero correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  in1 : Interval(0, 0.5).1
        |    input  in2 : Interval[0, 0].1
        |    output mul : Interval
        |    mul <= mul(in2, in1)
        |    """.stripMargin
    val check = s"""output mul : Interval[0, 0].2 """.stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }

  "Interval types" should "infer muxes correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  p   : UInt<1>
        |    input  in1 : Interval(0, 0.5).1
        |    input  in2 : Interval[0, 0].1
        |    output out : Interval
        |    out <= mux(p, in2, in1)
        |    """.stripMargin
    val check = s"""output out : Interval[0, 0.5).1 """.stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }
  "Interval types" should "infer dshl correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  p   : UInt<3>
        |    input  in1 : Interval(-1, 0.5).0
        |    output out : Interval
        |    out <= dshl(in1, p)
        |    """.stripMargin
    val check = s"""output out : Interval(-7, 3.5).0 """.stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }
  "Interval types" should "infer asInterval correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  p   : UInt<3>
        |    output out : Interval
        |    out <= asInterval(p, 0, 4, 1)
        |    """.stripMargin
    val check = s"""output out : Interval[0, 2].1 """.stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }
  "Interval types" should "do wrap/clip correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  s:     SInt<2>
        |    input  u:     UInt<3>
        |    input  in1:   Interval[-3, 5].0
        |    output wrap1: Interval
        |    output wrap2: Interval
        |    output wrap3: Interval
        |    output wrap4: Interval
        |    output wrap5: Interval
        |    output wrap6: Interval
        |    output wrap7: Interval
        |    output clip1: Interval
        |    output clip2: Interval
        |    output clip3: Interval
        |    output clip4: Interval
        |    output clip5: Interval
        |    output clip6: Interval
        |    output clip7: Interval
        |    wrap1 <= wrap(in1, u)
        |    wrap2 <= wrap(in1, s)
        |    wrap3 <= wrap(in1, asInterval(s, -2, 4, 0))
        |    wrap4 <= wrap(in1, asInterval(s, -1, 1, 0))
        |    wrap5 <= wrap(in1, asInterval(s, -4, 4, 0))
        |    wrap6 <= wrap(in1, asInterval(s, -1, 7, 0))
        |    wrap7 <= wrap(in1, asInterval(s, -4, 7, 0))
        |    clip1 <= clip(in1, u)
        |    clip2 <= clip(in1, s)
        |    clip3 <= clip(in1, asInterval(s, -2, 4, 0))
        |    clip4 <= clip(in1, asInterval(s, -1, 1, 0))
        |    clip5 <= clip(in1, asInterval(s, -4, 4, 0))
        |    clip6 <= clip(in1, asInterval(s, -1, 7, 0))
        |    clip7 <= clip(in1, asInterval(s, -4, 7, 0))
        |    """.stripMargin
    val check = s"""
        |    output wrap1 : Interval[0, 7].0
        |    output wrap2 : Interval[-2, 1].0
        |    output wrap3 : Interval[-2, 4].0
        |    output wrap4 : Interval[-1, 1].0
        |    output wrap5 : Interval[-4, 4].0
        |    output wrap6 : Interval[-1, 7].0
        |    output wrap7 : Interval[-4, 7].0
        |    output clip1 : Interval[0, 5].0
        |    output clip2 : Interval[-2, 1].0
        |    output clip3 : Interval[-2, 4].0
        |    output clip4 : Interval[-1, 1].0
        |    output clip5 : Interval[-3, 4].0
        |    output clip6 : Interval[-1, 5].0
        |    output clip7 : Interval[-3, 5].0 """.stripMargin
        // TODO: this optimization
        //|    output wrap7 : Interval[-3, 5].0
    executeTest(input, check.split("\n") map normalized, passes)
  }
  "Interval types" should "remove wrap/clip correctly" in {
    val passes = Seq(ToWorkingIR, InferTypes, ResolveGenders, new InferWidths(), new RemoveIntervals())
      val input =
        s"""circuit Unit :
        |  module Unit :
        |    input  s:     SInt<2>
        |    input  u:     UInt<3>
        |    input  in1:   Interval[-3, 5].0
        |    output wrap1: Interval
        |    output wrap2: Interval
        |    output wrap3: Interval
        |    output wrap4: Interval
        |    output wrap5: Interval
        |    output wrap6: Interval
        |    output wrap7: Interval
        |    output clip1: Interval
        |    output clip2: Interval
        |    output clip3: Interval
        |    output clip4: Interval
        |    output clip5: Interval
        |    output clip6: Interval
        |    output clip7: Interval
        |    wrap1 <= wrap(in1, u)
        |    wrap2 <= wrap(in1, s)
        |    wrap3 <= wrap(in1, asInterval(s, -2, 4, 0))
        |    wrap4 <= wrap(in1, asInterval(s, -1, 1, 0))
        |    wrap5 <= wrap(in1, asInterval(s, -4, 4, 0))
        |    wrap6 <= wrap(in1, asInterval(s, -1, 7, 0))
        |    wrap7 <= wrap(in1, asInterval(s, -4, 7, 0))
        |    clip1 <= clip(in1, u)
        |    clip2 <= clip(in1, s)
        |    clip3 <= clip(in1, asInterval(s, -2, 4, 0))
        |    clip4 <= clip(in1, asInterval(s, -1, 1, 0))
        |    clip5 <= clip(in1, asInterval(s, -4, 4, 0))
        |    clip6 <= clip(in1, asInterval(s, -1, 7, 0))
        |    clip7 <= clip(in1, asInterval(s, -4, 7, 0))
        |    """.stripMargin
    val check = s"""
        |    wrap1 <= mux(lt(in1, SInt<0>("h0")), add(in1, SInt<5>("h8")), in1)
        |    wrap2 <= asSInt(bits(in1, 1, 0))
        |    wrap3 <= mux(gt(in1, SInt<4>("h4")), sub(in1, SInt<4>("h7")), mux(lt(in1, SInt<2>("h-2")), add(in1, SInt<4>("h7")), in1))
        |    wrap4 <= add(rem(sub(in1, SInt<1>("h-1")), sub(SInt<2>("h1"), SInt<1>("h-1"))), SInt<1>("h-1"))
        |    wrap5 <= mux(gt(in1, SInt<4>("h4")), sub(in1, SInt<5>("h9")), in1)
        |    wrap6 <= mux(lt(in1, SInt<1>("h-1")), add(in1, SInt<5>("h9")), in1)
        |    wrap7 <= in1
        |    clip1 <= mux(lt(in1, SInt<0>("h0")), SInt<0>("h0"), in1)
        |    clip2 <= mux(gt(in1, SInt<2>("h1")), SInt<2>("h1"), mux(lt(in1, SInt<2>("h-2")), SInt<2>("h-2"), in1))
        |    clip3 <= mux(gt(in1, SInt<4>("h4")), SInt<4>("h4"), mux(lt(in1, SInt<2>("h-2")), SInt<2>("h-2"), in1))
        |    clip4 <= mux(gt(in1, SInt<2>("h1")), SInt<2>("h1"), mux(lt(in1, SInt<1>("h-1")), SInt<1>("h-1"), in1))
        |    clip5 <= mux(gt(in1, SInt<4>("h4")), SInt<4>("h4"), in1)
        |    clip6 <= mux(lt(in1, SInt<1>("h-1")), SInt<1>("h-1"), in1)
        |    clip7 <= in1
        """.stripMargin
    executeTest(input, check.split("\n") map normalized, passes)
  }
}