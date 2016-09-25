import com.google.inject.AbstractModule
import org.splink.raven.{Assembly, BricksController}

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[BricksController]).to(classOf[Assembly])
  }

}
