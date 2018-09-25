package net.scalax.asuna.sample.dto2

import net.scalax.asuna.core.decoder.{DecoderShape, SplitData}
import net.scalax.asuna.mapper.common.RepColumnContent
import net.scalax.asuna.mapper.decoder.{DecoderContent, DecoderHelper, DecoderWrapperHelper}

import scala.concurrent.{ExecutionContext, Future}

trait FutureDtoWrapper[RepOut, DataType] extends DecoderContent[RepOut, DataType] {
  def model(implicit ec: ExecutionContext): Future[DataType]
}

trait FutureDtoHelper {

  object dtoF extends DecoderHelper[Future[(Any, Any)], (Any, Any)] with DecoderWrapperHelper[Future[(Any, Any)], (Any, Any), FutureDtoWrapper] {
    override def effect[Rep, D, Out](rep: Rep)(implicit shape: DecoderShape.Aux[Rep, D, Out, Future[(Any, Any)], (Any, Any)]): FutureDtoWrapper[Out, D] = {
      val wrapCol = shape.wrapRep(rep)
      val colsF   = shape.toLawRep(wrapCol, Future.successful(null))
      new FutureDtoWrapper[Out, D] {
        override def model(implicit ec: ExecutionContext): Future[D] = {
          val data = colsF.map(cols => shape.takeData(wrapCol, cols))
          data.map(_.current)
        }
      }
    }
  }

  implicit def futureDtoShapeImplicit1[T](implicit ec: ExecutionContext): DecoderShape.Aux[RepColumnContent[T, T], T, T, Future[(Any, Any)], (Any, Any)] =
    new DecoderShape[RepColumnContent[T, T], Future[(Any, Any)], (Any, Any)] {
      override type Target = T
      override type Data   = T
      override def wrapRep(base: RepColumnContent[T, T]): T                          = base.rep
      override def toLawRep(base: T, oldRep: Future[(Any, Any)]): Future[(Any, Any)] = oldRep.map(r => (base, r))
      override def takeData(rep: T, oldData: (Any, Any)): SplitData[T, (Any, Any)] =
        SplitData(current = oldData._1.asInstanceOf[T], left = oldData._2.asInstanceOf[(Any, Any)])
    }

  implicit def futureDtoShapeImplicit2[T](
      implicit ec: ExecutionContext
  ): DecoderShape.Aux[RepColumnContent[Future[T], T], T, Future[T], Future[(Any, Any)], (Any, Any)] =
    new DecoderShape[RepColumnContent[Future[T], T], Future[(Any, Any)], (Any, Any)] {
      override type Target = Future[T]
      override type Data   = T
      override def wrapRep(base: RepColumnContent[Future[T], T]): Future[T] = base.rep
      override def toLawRep(base: Future[T], oldRep: Future[(Any, Any)]): Future[(Any, Any)] = {
        for {
          oldData  <- oldRep
          baseData <- base
        } yield {
          (baseData, oldData)
        }
      }
      override def takeData(rep: Future[T], oldData: (Any, Any)): SplitData[T, (Any, Any)] =
        SplitData(current = oldData._1.asInstanceOf[T], left = oldData._2.asInstanceOf[(Any, Any)])
    }

}
