### asuna 中文教程

# 第一篇: 简介篇

asuna 是 scala 的一个类型驱动的解决函数式对象映射(Functional Object
Mapping)问题的库，可以用来解决按属性名称对应的读写器实例和 case class 之间的映射问题。

#### 1.问题情境

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