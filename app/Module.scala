import com.google.inject.AbstractModule
import org.splink.raven.{Assembly, PageletController}

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[PageletController]).to(classOf[Assembly])
  }

}
