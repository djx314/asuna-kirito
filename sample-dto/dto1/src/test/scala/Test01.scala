package net.scalax.asuna.sample.dto1

import net.scalax.asuna.mapper.common.annotations.RootTable
import net.scalax.asuna.mapper.decoder.LazyData

import scala.annotation.meta.field

object Test01 extends DtoHelper with App {

  case class TargetModel(id: Int, name: String, age: Int, describe: String)

  case class SourceModel1(id: Int, name: String, age: Int, describe: String)
  //简单变换
  val source1             = SourceModel1(2333, "miaomiaomiao", 12, "wangwangwang")
  val model1: TargetModel = dto.effect(dto.modelOnly[TargetModel](source1).compile).model
  println(model1)

  case class SourceModel2(age: Int, describe: String)
  class SourceModel2Ext(@(RootTable @field) val rootModel: SourceModel2) {
    val id   = 2333
    val name = "miaomiaomiao"
  }
  //扩展现有属性
  val source2             = SourceModel2(12, "wangwangwang")
  val model2: TargetModel = dto.effect(dto.modelOnly[TargetModel](new SourceModel2Ext(source2)).compile).model
  println(model2)

  case class SourceModel3(id: String, name: String, age: Int, describe: Int)
  class SourceModel3Ext(@(RootTable @field) val rootModel: SourceModel3) {
    val id       = 2333
    val name     = "miaomiaomiao"
    val describe = "wangwangwang"
  }
  //重写现有属性
  val source3             = SourceModel3("error id", "error name", 12, Int.MaxValue)
  val model3: TargetModel = dto.effect(dto.modelOnly[TargetModel](new SourceModel3Ext(source3)).compile).model
  println(model3)

  case class SourceModel4(age: Int, describe: Int)
  case class IdGen(id: Int, name: String)
  case class SubPro(age: Int)
  class SourceModel4Ext(@(RootTable @field) val rootModel: SourceModel4) {
    val describe = "wangwangwang"
  }
  //懒加载
  val source4                                      = SourceModel4(age = 12, describe = Int.MaxValue)
  val model4: LazyData[IdGen, TargetModel, SubPro] = dto.effect(dto.lazyData[IdGen, TargetModel, SubPro](new SourceModel4Ext(source4)).compile).model
  println(model4(IdGen(2333, "miaomiaomiao")))
  println(model4.sub)

}
