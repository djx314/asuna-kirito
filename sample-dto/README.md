### asuna 中文教程

# 第一篇: 简介

asuna 是 Scala 的一个类型驱动的提供函数式对象映射(Functional Object
Mapping)支持的库，可以用来解决按属性名称对应的读写器实例和 case class
之间的映射问题，并且不会使用运行时反射而带来额外的开销。

#### 1. 问题情境

观察一段 slick 代码

```scala
case class User(id: Option[Int], first: String, last: String)

class Users(tag: Tag) extends Table[User](tag, "users") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def first = column[String]("first")
  def last = column[String]("last")
  def * = (id.?, first, last) <> (User.tupled, User.unapply)
}

val users = TableQuery[Users]
```

这是 slick 官网的一段 Table 声明代码。容易发现，Table
中`def id`、`def first`、`def last`都是必要的声明，无法省略。但方法`*`却需要编写大量的样板代码。当
Table 的列数达到 20 列以上后，列和对象的对应关系将会变得非常复杂。asuna
为列并非是严格的 Type Class 并且有部分异构的 Table
实例提供了一个映射的底层，使得它能够灵活地应对各种自定义的映射逻辑，并且这个映射是类型驱动的，理论上可以解决循环类型引用的问题。

不过更换 slick 的 Table Shape 这个课题涉及到比较复杂的技巧，我们将会留到第三章详细讨论。

#### 2. 设计一个 DTO(Data Transfer Object) 转换逻辑

在 Java 中，我们可以使用 Dozer 来转换 DTO，但是在 Scala 中没有太好的替代，[henkan](https://github.com/kailuowang/henkan)
这个库实现了类似的功能但并不完善。

其实使用 asuna 就可以实现类似的功能，而且实现的方式并不复杂，现在就让我们逐步来实现一个结构基本相同，但有部分异构的
case class 之间的转换逻辑吧。

下文中我们把存放读写器的实例称为 Table，把 case class 称为 Model。

观察上文 slick 的例子，Table 里面的列声明类型系统相对复杂，有
Rep[T]、Rep[Option[T]] 等基本类型，也有
(Rep[Int], (Rep[LOng], Rep[Option[String]])) 等复合类型，slick
内部使用一个叫 Shape 的类来把这些列析构成类似 List[Rep[_]] 的结构再把数据库查询到的诸如
List[Any] 类型的数据重新构造出需要的类型。

asuna 也使用了类似的概念，在 asuna 中，主要使用了
EncoderShape 和 DecoderShape 来进行映射逻辑的封装。EncoderShape 主要负责把 Model
转化成统一的抽象，类似 circe 的 Encoder，而 DecoderShape 主要负责把统一抽象的实例转换成特定的
Model，类似 circe 的 Decoder。

其实大家已经注意到了，slick 的 Shape 包括了 Encoder 和 Decoder
功能，对很多情景来说都不太友好，所以借用了 circe 的 Encoder 和 Decoder
概念，以构建起 EncoderShape 和 DecoderShape 两个抽象。

那一个 DTO 的转换对应 EncoderShape 还是 DecoderShape 呢？在 DTO 中，列之间的转换最普遍的情况是
T => T，我们可以把它看成 Unit 经过 Id[T] 析构成 T，恰好配对 circe 的情景: Json 经过 Decoder[T]
析构成 Either[Exception, T]，所以一个 DTO 转换应该对应 DecoderShape。现在让我们来看看
DecoderShape 这个抽象:

```scala
trait DecoderShape[-E, RepCol, DataCol] extends CommonShape[E, RepCol, DataCol] {
  self =>

  type Target
  type Data

  def packed: DecoderShape.Aux[Target, Data, Target, RepCol, DataCol] //implemented
  def wrapRep(base: E): Target
  def toLawRep(base: Target, oldRep: RepCol): RepCol
  def takeData(rep: Target, oldData: DataCol): SplitData[Data, DataCol]
  def dmap[T](f: (Target, Data) => T): DecoderShape.Aux[E, T, self.Target, RepCol, DataCol] //implemented

}

object DecoderShape extends ListDecoderShapeImplicit {
  type Aux[-E, D, T, RepCol, DataCol] = DecoderShape[E, RepCol, DataCol] { type Target = T; type Data = D }
}
```

其中，E 对应 Table 中每一列的类型。

Target 代表 E 经过 wrapRep 处理之后的类型，在 DecoderShape
里面的其他地方都使用 Target 作为参数做其他处理，这样在一些需要把原始类型 lift
成包装类型的场景下，可以避免每调用一次 DecoderShape 都 lift 一次而造成性能损耗。

Data 类型代表该 DecoderShape 所对应的数据类型，类似 T 至于 Decoder[T]。

Target 和 Data 都是根据 E 决定的，所以用 Dependent Type 表示。

在方法 toLawRep 中，可以见到对 RepCol 进行了叠加操作，可以使用自建的逻辑，把 Table
中的列逐个叠加到 RepCol 类型中，然后我们需要根据这个统一的 RepCol 生成一个意料之中的
DataCol，经由方法 takeData 进行逐列析构。

另外有两个已经实现了的方法，packed 负责把 DecoderShape 的 E lift 成 Target。dmap
的功能类似 Functor 的 map，但类型更加复杂。

现在让我们应用一下，实现一个 DTO 的转换逻辑。

```scala
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
```

仅用 30 行左右的代码，即实现了一个 DTO 转换的逻辑。可以看到，DecoderShape 一般以 implicit
的形式提供。请注意这里的 Rep 不是 T，原则上是可以使用 T
的，但作为隐式转换而言限制太少，容易造成编译问题，所以这里使用了
RepColumnContent[T, T]，第一个类型参数代表 Table 的列的类型(这里是 Id[T]，即
T)。第二个参数代表期望的 Model 属性数据类型。

在这个 DecoderShape 的实现中，我们可以看到，wrapRep 做了一个简单的转换，把
RepColumnContent[T, T] 变成了方便处理的类型 T。wrapRep
在逐个叠加列到一个统一的数据类型，这里为了性能更好，我们选择了
Tuple2[Any, Any]，其实根据这里的作用，选择 List[Any] 也是可以的。注意，在
takeData 中我们获取数据的方向跟 toLawRep
是相反的，这样我们可以方便地使用一些基于栈的数据类型。而之后的例子中我们会看到，由于 EncoderShape
的 Model 数据是在方法的参数端，takeData 的方向跟 toLawRep 是相同的。

在 object dto 中，我们定义了一系列上下文的操作，可以注意到 DecoderWrapperHelper、DtoWrapper 和
effect 方法这 3 个实现是可选的，亦可以根据喜好自己实现。而 DecoderHelper
则封装了一些基础的操作方法，包括一些 DecoderShape 的基本操作和 Table <-> Model
映射的宏操作。

现在让我们来看看这个映射到底能实现什么功能。

基础类型定义

```scala
case class TargetModel(id: Int, name: String, age: Int, describe: String)
```

```scala
case class SourceModel1(id: Int, name: String, age: Int, describe: String)
//简单变换
val source1             = SourceModel1(2333, "miaomiaomiao", 12, "wangwangwang")
val model1: TargetModel = dto.effect(dto.modelOnly[TargetModel](source1).compile).model
println(model1) //TargetModel(2333,miaomiaomiao,12,wangwangwang)
```

首先我们可以实现一个最基本的 DTO 转换，在属性相同类型匹配的情况下直接转换 SourceModel1 到目标类型
TargetModel。

```scala
case class SourceModel2(age: Int, describe: String)
class SourceModel2Ext(@(RootTable @field) val rootModel: SourceModel2) {
    val id   = 2333
    val name = "miaomiaomiao"
}
//扩展现有属性
val source2             = SourceModel2(12, "wangwangwang")
val model2: TargetModel = dto.effect(dto.modelOnly[TargetModel](new SourceModel2Ext(source2)).compile).model
println(model2) //TargetModel(2333,miaomiaomiao,12,wangwangwang)
```

SourceModel2 中如果有部分属性需要附加，可以使用 RootTable 注解。RootTable
会把该属性的所有子属性全部附加到 SourceModel2Ext 中作为一级属性，而 SourceModel2Ext
中的一级属性必然具有更高的优先级，所以这一特性还可以用来覆盖现有属性。

注意：rootModel 在加了 RootTable
注解之后不可以再作为普通属性使用，所以一般不要使用带有歧义的属性名称。

```scala
case class SourceModel3(id: String, name: String, age: Int, describe: Int)
class SourceModel3Ext(@(RootTable @field) val rootModel: SourceModel3) {
    val id       = 2333
    val name     = "miaomiaomiao"
    val describe = "wangwangwang"
}
//重写现有属性
val source3             = SourceModel3("error id", "error name", 12, Int.MaxValue)
val model3: TargetModel = dto.effect(dto.modelOnly[TargetModel](new SourceModel3Ext(source3)).compile).model
println(model3) //TargetModel(2333,miaomiaomiao,12,wangwangwang)
```

上述例子就体现了如何覆盖现有属性，id、name、describe 这 3 个属性不论类型都将由 SourceModel3Ext
里面的一级属性覆盖。

而如果有些 Model 的属性还不能在一开始就决定，例如需要从其他数据源中获取 id 值和 name
值才能构造成一个完整的 TargetModel，该如何操作呢？asuna 提供了一个 LazyData 的操作，

```scala
case class SourceModel4(age: Int, describe: Int)
case class IdGen(id: Int, name: String)
case class SubPro(age: Int)
class SourceModel4Ext(@(RootTable @field) val rootModel: SourceModel4) {
    val describe = "wangwangwang"
}
//懒加载
val source4                      = SourceModel4(age = 12, describe = Int.MaxValue)
val model4: LazyModel[IdGen, TargetModel, SubPro] = dto.effect(dto.lazyModel[IdGen, TargetModel, SubPro](new SourceModel4Ext(source4)).compile).model
println(model4(IdGen(2333, "miaomiaomiao"))) //TargetModel(2333,miaomiaomiao,12,wangwangwang)
println(model4.sub) //SubPro(12)
```

他将会自动检测 IdGen 的列，不对 SourceModel4Ext 中相同名称的属性进行求值，而是直接生成一个
LazyModel[IdGen, TargetModel, SubPro]。LazyModel[IdGen, TargetModel, SubPro] 是 IdGen => TargetModel
的子类，并且直接通过 sub 属性对外暴露
SubPro，以方便在还未能求值的前提下暴露一些已经有值的列(这里是 age)。

#### 3. 设计一个支持 Future 的 DTO 转换逻辑

blablabla
