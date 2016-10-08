[![Build Status](https://travis-ci.org/splink/pagelets.svg?branch=master)](https://travis-ci.org/splink/pagelets)

# Pagelets
A Module for the Play Framework to build modular applications in an elegant and concise manner.


### Idea
The idea behind the Pagelets Module is to split a web page into small, composable units. Such a unit is called a pagelet. In terms of the Play Framework a pagelet is just an Action[AnyContent]. That means that a pagelet is basically a (small) web page. Thus any pagelet can also be served individually. A pagelet usually consists of a view, assets (JavaScript, Css), a controller action and a service to fetch data.

*Pagelets are particularly useful if you want to serve tailor-made pages to your visitors. For instance you can easily serve a slightly different page to users from different countries (i18n), or perform A/B testing, or fine-tune the page based on the user. (logged-in, gender, other preferences, ...)*


### Traits
- **composable**: multiple pagelets can be composed into a page. A page is just a tree of pagelets. Any part of the pagelet tree can be served to the user. 
- **resilient**: if a pagelet fails, a fallback is served. Other pagelets are not affected by the failure of one or more pagelets. 
- **simple**: to create a pagelet is simple compared to a whole page, because of it's limited scope. To compose a page from pagelets is simple.
- **modular**: any pagelet can be easily swapped with another pagelet, removed or added to a page at runtime.


Pagelets are non invasive and not opinionated: You can stick to your code style and apply the patterns you prefer. Use your favorite dependency injection mechanism and template engine. You don't need to apply the goodness of pagelets everywhere, only employ pagelets where you need them. Pagelets also do not introduce additional dependencies to your project. 

### Usage
The pagelets Module requires Play Framework 2.5.x
Add the following lines to your build.sbt file:

```scala
libraryDependencies += "org.splink" %% "pagelets" % "0.0.1"
routesImport += "org.splink.pagelets.Binders._"
```

Add the following line to your application.conf file:
```
play.modules.enabled += org.splink.pagelets.pageletModule
```

Big thanks to [brikis98](https://github.com/brikis98) who originally had the idea to split a page into pagelets.