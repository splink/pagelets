import com.google.inject.AbstractModule
import java.time.Clock

import org.splink.raven.{Assembly, BricksController}
import services.{ApplicationTimer, AtomicCounter, Counter}

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[BricksController]).to(classOf[Assembly])
  }

}
