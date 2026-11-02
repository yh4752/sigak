package com.sigak.source.runner

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sigak.collection.runner.ApplicationExit
import com.sigak.collection.runner.CollectionRunCommandOutput
import com.sigak.source.service.SourceManagementService
import com.sigak.support.sourceDefinitionFixture
import kotlin.test.*
import org.mockito.Mockito.*
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.env.MockEnvironment

class SourceCommandRunnerTest {
    private val service=mock(SourceManagementService::class.java)
    private val lines=mutableListOf<String>()
    private val errors=mutableListOf<String>()
    private val codes=mutableListOf<Int>()
    private val environment=MockEnvironment().withProperty("spring.main.web-application-type","none")
        .withProperty("sigak.collection.automation.enabled","false").withProperty("sigak.summary.enabled","false")
        .withProperty("sigak.summary.worker-enabled","false").withProperty("sigak.fulltext.worker-enabled","false")
    private val output=object:CollectionRunCommandOutput {
        override fun writeLine(value:String) { lines.add(value) }
        override fun writeErrorLine(value:String) { errors.add(value) }
    }
    private fun runner()=SourceCommandRunner(service,jacksonObjectMapper().findAndRegisterModules(),ApplicationExit { codes.add(it) },output,environment)

    @Test fun `source operator mode forces paid workers scheduler and web server off before boot`() {
        val args=com.sigak.sourceOperatorArguments(arrayOf("--sigak.sources.command=list","--sigak.summary.enabled=true"))
        assertTrue(args.contains("--sigak.summary.enabled=false"))
        assertFalse(args.contains("--sigak.summary.enabled=true"))
        assertTrue(args.contains("--spring.main.web-application-type=none"))
        assertTrue(args.contains("--sigak.fulltext.worker-enabled=false"))
    }
    @Test fun `list returns machine readable output and exits cleanly`() {
        `when`(service.list()).thenReturn(emptyList())
        runner().run(DefaultApplicationArguments("--sigak.sources.command=list"))
        assertEquals(listOf(0),codes)
        assertEquals("SIGAK_SOURCE_RESULT=[]",lines.single())
        assertTrue(errors.isEmpty())
    }
    @Test fun `unsafe worker configuration rejects command before calling service`() {
        environment.withProperty("sigak.summary.enabled","true")
        runner().run(DefaultApplicationArguments("--sigak.sources.command=list"))
        assertEquals(listOf(1),codes)
        assertTrue(errors.single().contains("UNSAFE_SOURCE_COMMAND_MODE"))
        verifyNoInteractions(service)
    }
    @Test fun `enable requires an explicit current version and sample review`() {
        val source=sourceDefinitionFixture()
        `when`(service.enable("key",7,true,true)).thenReturn(source)
        runner().run(DefaultApplicationArguments("--sigak.sources.command=enable","--sigak.sources.key=key","--sigak.sources.version=7","--sigak.sources.reviewed","--sigak.sources.feed-reviewed"))
        verify(service).enable("key",7,true,true)
        assertEquals(listOf(0),codes)
    }
    @Test fun `unknown options missing versions and patch typos fail without running service`() {
        for (args in listOf(
            arrayOf("--sigak.sources.command=list","--sigak.sources.typo=bad"),
            arrayOf("--sigak.sources.command=enable","--sigak.sources.key=key"),
            arrayOf("--sigak.sources.command=configure","--sigak.sources.key=key","--sigak.sources.version=1","--sigak.sources.patch={\"bodyTypo\":\"PAGE\"}")
        )) runner().run(DefaultApplicationArguments(*args))
        assertEquals(listOf(1,1,1),codes)
        verifyNoInteractions(service)
    }
    @Test fun `registering an existing source does not recheck or disable it`() {
        val source=sourceDefinitionFixture()
        `when`(service.register("https://engineering.example.test/")).thenReturn(source)
        runner().run(DefaultApplicationArguments("--sigak.sources.command=register","--sigak.sources.url=https://engineering.example.test/"))
        verify(service,never()).check(source.key,source.version)
        assertEquals(listOf(0),codes)
    }

}
