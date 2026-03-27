package build.ascribe.tckrunner

import org.junit.platform.suite.api.*

@Suite
@IncludeEngines(Array("cucumber"))
@SelectClasspathResource("features")
class TckSuite
