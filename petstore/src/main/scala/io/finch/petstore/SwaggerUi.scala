package io.finch.petstore

import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.io.Reader
import com.twitter.util.Future
import io.finch.response.NotFound
import io.finch.route._

trait SwaggerUi {
  def getFile(path: String, contentType: String): Future[Response] = {
  	val reader = Reader.fromStream(getClass.getResourceAsStream(path))

    Reader.readAll(reader).map { buf =>
      val response = Response()
      response.contentType = contentType
      response.content = buf
      response
    }

  }

	def swaggerUi: Router[Response] = css | lang | lib | root | base

  private[this] val cssFiles =
    Set("print", "reset", "screen", "style", "typography").map(_ + ".css")

  private[this] val langFiles =
    Set("en", "es", "pt", "ru", "translator").map(_ + ".js")

  private[this] val css: Router[Response] =
    get("swagger" / "css" / string).embedFlatMap { file =>
    	if (cssFiles(file))
    	  getFile(s"/swagger/css/$file", "text/css")
    	else Future.value(NotFound())
    }

  private[this] val lang: Router[Response] =
    get("swagger" / "lang" / string).embedFlatMap { file =>
    	if (langFiles(file))
    	  getFile(s"/swagger/lang/$file", "application/javascript")
    	else Future.value(NotFound())
    }

  private[this] val lib: Router[Response] =
    get("swagger" / "lib" / string).embedFlatMap { file =>
    	getFile(s"/swagger/lib/$file", "application/javascript")
    }

	private[this] val base: Router[Response] =
	  get("swagger").embedFlatMap(_ => getFile("/swagger/index.html", "text/html"))

	private[this] val root: Router[Response] =
	  get("swagger" / string).embedFlatMap {
	  	case "petstore.json" => getFile("/swagger/petstore.json", "application/json")
	    case "index.html" => getFile("/swagger/index.html", "text/html")
	    case "o2c.html" => getFile("/swagger/o2c.html", "text/html")
	    case "swagger-ui.js" => getFile("/swagger/swagger-ui.js", "application/javascript")
	    case _ => Future.value(NotFound())
    }
}