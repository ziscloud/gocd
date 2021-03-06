/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.agent;

import com.thoughtworks.go.agent.service.AgentUpgradeService;
import com.thoughtworks.go.agent.service.SslInfrastructureService;
import com.thoughtworks.go.config.AgentRegistry;
import com.thoughtworks.go.config.GuidService;
import com.thoughtworks.go.plugin.access.packagematerial.PackageAsRepositoryExtension;
import com.thoughtworks.go.plugin.access.pluggabletask.TaskExtension;
import com.thoughtworks.go.plugin.access.scm.SCMExtension;
import com.thoughtworks.go.plugin.infra.PluginManager;
import com.thoughtworks.go.plugin.infra.PluginManagerReference;
import com.thoughtworks.go.publishers.GoArtifactsManipulator;
import com.thoughtworks.go.remote.AgentIdentifier;
import com.thoughtworks.go.remote.BuildRepositoryRemote;
import com.thoughtworks.go.remote.work.Work;
import com.thoughtworks.go.server.service.AgentRuntimeInfo;
import com.thoughtworks.go.util.SubprocessLogger;
import com.thoughtworks.go.util.SystemEnvironment;
import com.thoughtworks.go.util.SystemUtil;
import com.thoughtworks.go.util.command.EnvironmentVariableContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.thoughtworks.go.util.SystemUtil.getFirstLocalNonLoopbackIpAddress;
import static com.thoughtworks.go.util.SystemUtil.getLocalhostName;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;


public class AgentControllerTest {
    @Mock
    private BuildRepositoryRemote loopServer;
    @Mock
    private GoArtifactsManipulator artifactsManipulator;
    @Mock
    private SslInfrastructureService sslInfrastructureService;
    @Mock
    private Work work;
    @Mock
    private AgentRegistry agentRegistry;
    @Mock
    private SubprocessLogger subprocessLogger;
    @Mock
    private SystemEnvironment systemEnvironment;
    @Mock
    private AgentUpgradeService agentUpgradeService;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private PackageAsRepositoryExtension packageAsRepositoryExtension;
    @Mock
    private SCMExtension scmExtension;
    @Mock
    private TaskExtension taskExtension;

    private String agentUuid = "uuid";
    private AgentIdentifier agentIdentifier;
    private AgentController agentController;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        agentIdentifier = new AgentIdentifier(getLocalhostName(), getFirstLocalNonLoopbackIpAddress(), agentUuid);
    }

    @After
    public void tearDown() {
        GuidService.deleteGuid();
    }


    @Test
    public void shouldSetPluginManagerReference() throws Exception {
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        assertThat(PluginManagerReference.reference().getPluginManager(),is(pluginManager));
    }

    @Test
    public void shouldRetrieveWorkFromServerAndDoIt() throws Exception {
        when(loopServer.getWork(any(AgentRuntimeInfo.class))).thenReturn(work);
        when(agentRegistry.uuid()).thenReturn(agentUuid);
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        agentController.ping();
        agentController.retrieveWork();
        verify(work).doWork(eq(agentIdentifier), eq(loopServer), eq(artifactsManipulator), any(EnvironmentVariableContext.class), any(AgentRuntimeInfo.class), eq(packageAsRepositoryExtension), eq(scmExtension), eq(taskExtension));
        verify(sslInfrastructureService).createSslInfrastructure();
    }

    @Test
    public void shouldRetriveCookieIfNotPresent() throws Exception {
        AgentRuntimeInfo infoWithCookie = AgentRuntimeInfo.fromAgent(new AgentIdentifier(SystemUtil.getLocalhostName(), SystemUtil.getFirstLocalNonLoopbackIpAddress(), agentUuid), "cookie",
                null);
        when(loopServer.getCookie(any(AgentIdentifier.class), eq(infoWithCookie.getLocation()))).thenReturn("cookie");
        when(sslInfrastructureService.isRegistered()).thenReturn(true);
        when(loopServer.getWork(infoWithCookie)).thenReturn(work);
        when(agentRegistry.uuid()).thenReturn(agentUuid);
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        agentController.loop();
        verify(work).doWork(eq(agentIdentifier), eq(loopServer), eq(artifactsManipulator), any(EnvironmentVariableContext.class), eq(infoWithCookie), eq(packageAsRepositoryExtension), eq(scmExtension), eq(taskExtension));
    }

    @Test
    public void shouldNotTellServerWorkIsCompletedWhenThereIsNoWork() throws Exception {
        when(loopServer.getWork(any(AgentRuntimeInfo.class))).thenReturn(work);
        when(agentRegistry.uuid()).thenReturn(agentUuid);
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        agentController.retrieveWork();
        verify(work).doWork(eq(agentIdentifier), eq(loopServer), eq(artifactsManipulator), any(EnvironmentVariableContext.class), any(AgentRuntimeInfo.class), eq(packageAsRepositoryExtension), eq(scmExtension), eq(taskExtension));
        verify(sslInfrastructureService).createSslInfrastructure();
    }

    @Test
    public void shouldReturnTrueIfCausedBySecurity() throws Exception {
        Exception exception = new Exception(new RuntimeException(new GeneralSecurityException()));
        when(agentRegistry.uuid()).thenReturn(agentUuid);

        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        assertTrue(agentController.isCausedBySecurity(exception));
        verify(sslInfrastructureService).createSslInfrastructure();
    }

    @Test
    public void shouldReturnFalseIfNotCausedBySecurity() throws Exception {
        Exception exception = new Exception(new IOException());
        when(agentRegistry.uuid()).thenReturn(agentUuid);


        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        assertFalse(agentController.isCausedBySecurity(exception));
        verify(sslInfrastructureService).createSslInfrastructure();
    }

    @Test
    public void shouldRegisterSubprocessLoggerAtExit() throws Exception {
        SslInfrastructureService sslInfrastructureService = mock(SslInfrastructureService.class);
        AgentRegistry agentRegistry = mock(AgentRegistry.class);
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        verify(subprocessLogger).registerAsExitHook("Following processes were alive at shutdown: ");
    }

    @Test
    public void shouldNotPingIfNotRegisteredYet() throws Exception {
        when(agentRegistry.uuid()).thenReturn(agentUuid);
        when(sslInfrastructureService.isRegistered()).thenReturn(false);

        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        agentController.ping();
        verify(sslInfrastructureService).createSslInfrastructure();
    }

    @Test
    public void shouldPingIfAfterRegistered() throws Exception {
        when(agentRegistry.uuid()).thenReturn(agentUuid);
        when(sslInfrastructureService.isRegistered()).thenReturn(true);
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        agentController.init();
        agentController.ping();
        verify(sslInfrastructureService).createSslInfrastructure();
        verify(loopServer).ping(any(AgentRuntimeInfo.class));
    }

    @Test
    public void shouldUpgradeAgentBeforeAgentRegistration() throws Exception {
        agentController = new AgentController(loopServer, artifactsManipulator, sslInfrastructureService, agentRegistry, agentUpgradeService, subprocessLogger, systemEnvironment,pluginManager, packageAsRepositoryExtension, scmExtension, taskExtension);
        InOrder inOrder = inOrder(agentUpgradeService, sslInfrastructureService);
        agentController.loop();
        inOrder.verify(agentUpgradeService).checkForUpgrade();
        inOrder.verify(sslInfrastructureService).registerIfNecessary();
    }
}
