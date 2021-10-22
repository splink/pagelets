package org.splink.pagelets

import play.api.mvc.PathBindable

object Binders {

  implicit object PathBindablePageletId extends PathBindable[PageletId] {
    def bind(key: String, value: String) = try {
      Right(PageletId(value))
    } catch {
      case _: Throwable =>
        Left(s"Can't create a PageletId from '$key'")
    }

    def unbind(key: String, value: PageletId): String = value.name
  }

}
