package net.scalax.asuna.sample.dto1

import net.scalax.asuna.core.decoder.{DecoderShape, SplitData}
import net.scalax.asuna.mapper.common.RepColumnContent
import net.scalax.asuna.mapper.decoder.{DecoderContent, DecoderHelper, DecoderWrapperHelper}

trait DtoWrapper[RepOut, DataType] extends DecoderContent[RepOut, DataType] {
  def model: DataType
}

trait DtoHelper {

  object dto extends DecoderHelper[(Any, Any), (Any, Any)] with DecoderWrapperHelper[(Any, Any), (Any, Any), DtoWrapper] {
    override def effect[Rep, D, Out](rep: Rep)(implicit shape: DecoderShape.Aux[Rep, D, Out, (Any, Any), (Any, Any)]): DtoWrapper[Out, D] = {
      val wrapCol = shape.wrapRep(rep)
      val cols    = shape.toLawRep(wrapCol, null)
      val data    = shape.takeData(wrapCol, cols)
      new DtoWrapper[Out, D] {
        override def model: D = data.current
      }
    }
  }

  implicit def dtoShapeImplicit1[T]: DecoderShape.Aux[RepColumnContent[T, T], T, T, (Any, Any), (Any, Any)] =
    new DecoderShape[RepColumnContent[T, T], (Any, Any), (Any, Any)] {
      override type Target = T
      override type Data   = T
      override def wrapRep(base: RepColumnContent[T, T]): T          = base.rep
      override def toLawRep(base: T, oldRep: (Any, Any)): (Any, Any) = (base, oldRep)
      override def takeData(rep: T, oldData: (Any, Any)): SplitData[T, (Any, Any)] =
        SplitData(current = oldData._1.asInstanceOf[T], left = oldData._2.asInstanceOf[(Any, Any)])
    }

}
