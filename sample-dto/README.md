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

其实大家已经注意到了