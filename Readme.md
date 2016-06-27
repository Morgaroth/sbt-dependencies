## Algorithm

* each scrapping round will be independent of before ones
* after scrapping generators will render source code of sbt-commons-auto library from templates
* library for templating will be chosen
* input for scrapping is: List of organisations to be scrapped with its types (Scala, Java)
    * optionally with excluded libraries
    * optionally with excluded library versions
* configuration 
* files have format:
```scala 
trait Organisation_1_Library_1 {
  val VER1 = ver("ver1")
  ... 
  val last = VER_N
}

object Organisation_1_Library_1 extends Organisation_LibraryName {
  def ver = s"org % name % _ "
  def apply(version_name) = ver(version_name)
}

trait Organisation_1 {
  val Library_1 = Organisation_1_Library_1
  
}

object Organisation_1 extends Organisation_1

trait ScalaLibraries 
  val ORG_1 = Organisation
  ...
}

trait JavaLibraries {
  val ORG_1 = Organisation
  ...
}

trait Libraries extends JavaLibraries with ScalaLibraries
```


#### TODO list

* group scala dependencies by scala version into one, align model to this
* add configuring by file and command line options
  * list of organisations (type - scala/java, source: maven/bintray)
  * list of aliases - possible aliases for names
  * integrate with existing sbt-commons plugin
  