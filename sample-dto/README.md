### asuna 中文教程

# 第一篇: 简介

asuna 是 Scala 的一个类型驱动的解决函数式对象映射(Functional Object
Mapping)问题的库，可以用来解决按属性名称对应的读写器实例和 case class
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
(Rep[Int], (Rep[LOng], Rep[Option[String]])) 等复杂类型，slick
内部是使用一个叫 Shape 的类来把这些列析构成类似 List[Rep[_]] 的结构再把数据库查询到的诸如
List[Any] 类型的数据重新构造成意料之中的类型。

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

仅需这 30 行左右的代码，一个 DTO 转换的逻辑即可实现。可以看到，DecoderShape 一般以 implicit
的形式存在。但这里的 Rep 不是 T，原则上是可以使用 T
的，但作为隐式转换而言限制太少，容易造成编译问题，所以这里使用了
RepColumnContent[T, T]，第一个类型参数代表 Table 的列的类型，第二个参数代表期望的 Model
属性数据类型，