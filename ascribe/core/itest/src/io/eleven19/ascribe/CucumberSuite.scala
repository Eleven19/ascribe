package io.eleven19.ascribe

import org.junit.platform.suite.api.*

@Suite
@IncludeEngines(Array("cucumber"))
@SelectClasspathResource("features")
class CucumberSuite
