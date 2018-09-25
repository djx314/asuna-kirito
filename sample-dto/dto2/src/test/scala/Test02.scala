package net.scalax.asuna.sample.dto1

import net.scalax.asuna.mapper.common.annotations.{RootDataProperty, RootTable}
import net.scalax.asuna.mapper.decoder.LazyData
import net.scalax.asuna.sample.dto2.FutureDtoHelper

import scala.annotation.meta.field
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object Test02 extends FutureDtoHelper with App {

  def await[R](f: Future[R]): R = Await.result(f, scala.concurrent.duration.Duration.Inf)

  case class TargetModel(id: Int, name: String, age: Int, describe: String)

  case class SourceModel1(id: Int, name: String, age: Int, describe: Future[String])
  //简单变换
  val source1                     = SourceModel1(2333, "miaomiaomiao", 12, Future.successful("wangwangwang"))
  val model1: Future[TargetModel] = dtoF.effect(dtoF.modelOnly[TargetModel](source1).compile).model
  println(await(model1))

  case class SourceModel2(age: Future[Int], describe: String)
  class SourceModel2Ext(@(RootTable @field) val rootModel: SourceModel2) {
    val id   = Future.successful(2333)
    val name = "miaomiaomiao"
  }
  //扩展现有属性
  val source2                     = SourceModel2(Future.successful(12), "wangwangwang")
  val model2: Future[TargetModel] = dtoF.effect(dtoF.modelOnly[TargetModel](new SourceModel2Ext(source2)).compile).model
  println(await(model2))

  case class SourceModel3(id: String, name: String, age: Int, describe: Int)
  class SourceModel3Ext(@(RootTable @field) val rootModel: SourceModel3) {
    val id       = 2333
    val name     = Future.successful("miaomiaomiao")
    val describe = Future.successful("wangwangwang")
  }
  //重写现有属性
  val source3                     = SourceModel3("error id", "error name", 12, Int.MaxValue)
  val model3: Future[TargetModel] = dtoF.effect(dtoF.modelOnly[TargetModel](new SourceModel3Ext(source3)).compile).model
  println(await(model3))

  case class SourceModel4(age: Future[Int], describe: Int)
  case class IdGen(id: Int, name: String)
  case class SubPro(age: Int)
  class SourceModel4Ext(@(RootTable @field) val rootModel: SourceModel4) {
    val describe = Future.successful("wangwangwang")
  }
  //懒加载
  val source4                                              = SourceModel4(age = Future.successful(12), describe = Int.MaxValue)
  val model4: Future[LazyData[IdGen, TargetModel, SubPro]] = dtoF.effect(dtoF.lazyData[IdGen, TargetModel, SubPro](new SourceModel4Ext(source4)).compile).model
  println(await(model4)(IdGen(2333, "miaomiaomiao")))
  println(await(model4).sub)

  case class SourceModel5(age: Int, describe: String)
  case class IdGen1(id: Int)
  class SourceModel5Ext(@(RootDataProperty[SourceModel5] @field) val rootModel: Future[SourceModel5]) {
    val name = Future.successful("miaomiaomiao")
  }
  //懒加载
  val source5F = Future.successful(SourceModel5(age = 12, describe = "wangwangwang"))
  val model5: Future[LazyData[IdGen1, TargetModel, SubPro]] =
    dtoF.effect(dtoF.lazyData[IdGen1, TargetModel, SubPro](new SourceModel5Ext(source5F)).compile).model
  println(await(model5)(IdGen1(2333)))
  println(await(model5).sub)

}
