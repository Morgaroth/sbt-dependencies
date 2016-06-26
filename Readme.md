## Algorithm

* each scrapping round will be independent of before ones
* after scrapping generators will render source code of sbt-commons-auto
library from templates
* library for templating will be chosen
* input for scrapping is: List of organisations to be scrapped with its types (Scala, Java)
    * optionally with excluded libraries
    * optionally with excluded library versions

* configuration 
* files have format:
```scala 
trait Organisation_LibraryName {
  val VER1 = ver("ver1")
  ... 
  val last = VER_N
}

object Organisation_LibraryName extends Organisation_LibraryName {
  def ver = s"org % name % _ "
  def apply(version_name) = ver(version_name)
}

trait Organisation {
  val LibraryName = Organisation_LibraryName
  
}

object Organisation extends Organisation

trait ScalaLibraries 
  val ORG_1 = Organisation
  ...
}

trait JavaLibraries {
  val ORG_1 = Organisation
  ...
}

```