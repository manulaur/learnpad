/**
 *
 */
package eu.learnpad.simulator.processmanager.activiti.processdispatcher;

/*
 * #%L
 * LearnPAd Simulator
 * %%
 * Copyright (C) 2014 - 2015 Linagora
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.runtime.ProcessInstance;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import eu.learnpad.sim.rest.data.ProcessInstanceData;
import eu.learnpad.simulator.IProcessEventReceiver;
import eu.learnpad.simulator.Main;
import eu.learnpad.simulator.monitoring.event.impl.ProcessEndSimEvent;
import eu.learnpad.simulator.monitoring.event.impl.TaskStartSimEvent;
import eu.learnpad.simulator.processmanager.ITaskRouter;
import eu.learnpad.simulator.processmanager.ITaskValidator;
import eu.learnpad.simulator.processmanager.activiti.ActivitiProcessManager;
import eu.learnpad.simulator.processmanager.activiti.taskrouter.ActivitiTaskRouter;
import eu.learnpad.simulator.utils.BPMNExplorer;

/**
 *
 * To speed up testing, we compare page MD5 digests instead of the full page
 * content
 *
 * @author Tom Jorquera - Linagora
 *
 */
public class ActivitiProcessDispatcherTest {

	public final static String TEST_PROCESS = "test.bpmn20.xml";
	public final static String TEST_PROCESS_KEY = "test";

	public final static List<String> TEST_PROCESS_USES = Arrays.asList("user1",
			"user2", "user3");

	static ProcessEngine processEngine;
	static EventForwarder eventForwarder;

	ProcessInstance processInstance;
	ProcessInstanceData processInstanceData;

	@BeforeClass
	public static void initActivitiEngine() {
		ProcessEngineConfiguration config = ProcessEngineConfiguration
				.createProcessEngineConfigurationFromInputStream(Main.class
						.getClassLoader().getResourceAsStream(
								Main.ACTIVITY_CONFIG_PATH));

		// create process engine
		processEngine = config.buildProcessEngine();

		// load test process
		processEngine.getRepositoryService().createDeployment()
		.addClasspathResource(TEST_PROCESS).deploy();

		eventForwarder = new EventForwarder();

		processEngine.getRuntimeService().addEventListener(eventForwarder,
				ActivitiEventType.PROCESS_COMPLETED);
		processEngine.getRuntimeService().addEventListener(eventForwarder,
				ActivitiEventType.ACTIVITY_COMPLETED);
		processEngine.getRuntimeService().addEventListener(eventForwarder,
				ActivitiEventType.ACTIVITY_STARTED);

	}

	@AfterClass
	public static void shutdownEngine() {
		processEngine.close();
	}

	@Before
	public void instanciateTestProcess() {
		// reset event forwarder
		eventForwarder.setDispatcher(null);

		// start process
		processInstance = processEngine.getRuntimeService()
				.startProcessInstanceByKey(TEST_PROCESS_KEY);

		processInstanceData = new ProcessInstanceData(processInstance.getId(),
				processInstance.getProcessDefinitionKey(),
				new HashMap<String, Object>(), TEST_PROCESS_USES,
				new HashMap<String, Collection<String>>());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProcessDispatcherInit() {

		ActivitiProcessManager processManger = mock(ActivitiProcessManager.class);
		IProcessEventReceiver processEventReceiver = mock(IProcessEventReceiver.class);

		new ActivitiProcessDispatcher(processInstanceData, processManger,
				processEventReceiver, processEngine.getTaskService(),
				processEngine.getRuntimeService(),
				processEngine.getRepositoryService(),
				processEngine.getHistoryService(), mock(ITaskRouter.class),
				mock(ITaskValidator.class), mock(BPMNExplorer.class)).start();

		// dispatcher should have dispatched first task
		// (since task processing is multithreaded to avoid blocking,
		// we may need to wait a little. 5 sec should be *far* more than
		// enough)
		ArgumentCaptor<TaskStartSimEvent> taskEvent = ArgumentCaptor
				.forClass(TaskStartSimEvent.class);
		verify(processEventReceiver, timeout(5000)).receiveTaskStartEvent(
				taskEvent.capture());
		assertEquals(taskEvent.getValue().task.processId,
				processInstance.getId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProcessDispatcherDispatch() throws InterruptedException {

		ActivitiProcessManager processManger = mock(ActivitiProcessManager.class);
		IProcessEventReceiver processEventReceiver = mock(IProcessEventReceiver.class);
		ITaskRouter taskRouter = mock(ITaskRouter.class);
		ITaskValidator<Map<String, Object>, Map<String, Object>> taskValidator = mock(ITaskValidator.class);

		when(taskRouter.route(any(String.class))).thenReturn(
				new HashSet<String>(Arrays.asList("user1")));

		// automatically validate tasks
		when(
				taskValidator.taskResultIsValid(anyString(), any(Map.class),
						any(Map.class))).thenReturn(true);

		final ActivitiProcessDispatcher dispatcher = new ActivitiProcessDispatcher(
				processInstanceData, processManger, processEventReceiver,
				processEngine.getTaskService(),
				processEngine.getRuntimeService(),
				processEngine.getRepositoryService(),
				processEngine.getHistoryService(), taskRouter, taskValidator,
				mock(BPMNExplorer.class));

		eventForwarder.setDispatcher(dispatcher);

		dispatcher.start();

		validateAllTasks(dispatcher, taskRouter, processEventReceiver);

		// should have processed 6 tasks in total
		ArgumentCaptor<TaskStartSimEvent> taskEvent = ArgumentCaptor
				.forClass(TaskStartSimEvent.class);
		verify(processEventReceiver, times(6)).receiveTaskStartEvent(
				taskEvent.capture());
		for (TaskStartSimEvent t : taskEvent.getAllValues()) {
			assertEquals(t.task.processId, processInstance.getId());
		}

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProcessDispatcherRouting() throws InterruptedException {

		@SuppressWarnings("serial")
		final Map<String, Collection<String>> routes = new HashMap<String, Collection<String>>() {
			{
				put("user1", Arrays.asList("user1", "user2", "user3"));
				put("user2", Arrays.asList("user2", "user3"));
				put("user3", Arrays.asList("user3"));
				put("user0", Arrays.asList("user3"));
			}
		};

		final ITaskRouter taskRouter = new ActivitiTaskRouter(
				processEngine.getTaskService(), routes);

		ActivitiProcessManager processManger = mock(ActivitiProcessManager.class);
		IProcessEventReceiver processEventReceiver = mock(IProcessEventReceiver.class);

		// automatically validate tasks
		ITaskValidator<Map<String, Object>, Map<String, Object>> taskValidator = mock(ITaskValidator.class);
		when(
				taskValidator.taskResultIsValid(anyString(), any(Map.class),
						any(Map.class))).thenReturn(true);

		final ActivitiProcessDispatcher dispatcher = new ActivitiProcessDispatcher(
				processInstanceData, processManger, processEventReceiver,
				processEngine.getTaskService(),
				processEngine.getRuntimeService(),
				processEngine.getRepositoryService(),
				processEngine.getHistoryService(), taskRouter, taskValidator,
				mock(BPMNExplorer.class));

		eventForwarder.setDispatcher(dispatcher);

		dispatcher.start();

		// dispatcher should have dispatched first task
		ArgumentCaptor<TaskStartSimEvent> taskEvent = ArgumentCaptor
				.forClass(TaskStartSimEvent.class);
		verify(processEventReceiver, timeout(5000).times(1))
		.receiveTaskStartEvent(taskEvent.capture());
		assertEquals(taskEvent.getValue().task.processId,
				processInstance.getId());

		// check task is correctly routed to users
		Set<String> expectedUserRoutes = new HashSet<String>();
		for (String role : getTaskRolesHelper(taskEvent.getValue().task.id)) {
			expectedUserRoutes.addAll(routes.get(role));
		}

		// both sets should be equal
		assertTrue(taskEvent.getValue().involvedusers
				.containsAll(expectedUserRoutes)
				&& expectedUserRoutes
				.containsAll(taskEvent.getValue().involvedusers));

		// automatically respond to next tasks dispatch
		doAnswer(new Answer<Void>() {
			public Void answer(InvocationOnMock invocation) throws Throwable {

				TaskStartSimEvent taskEvent = invocation.getArgumentAt(0,
						TaskStartSimEvent.class);
				Collection<String> receivedRoutes = taskEvent.involvedusers;

				// check task is correctly routed to users
				Set<String> expectedRoutes = new HashSet<String>();
				for (String role : getTaskRolesHelper(taskEvent.task.id)) {
					expectedRoutes.addAll(routes.get(role));
				}

				// both sets should be equal
				assertTrue(receivedRoutes.containsAll(expectedRoutes)
						&& expectedRoutes.containsAll(receivedRoutes));

				dispatcher.submitTaskCompletion(taskEvent.task, taskRouter
						.route(taskEvent.task.id).iterator().next(), null);
				return null;
			}
		}).when(processEventReceiver).receiveTaskStartEvent(
				any(TaskStartSimEvent.class));

		// respond to first task dispatch
		dispatcher.submitTaskCompletion(taskEvent.getValue().task, taskRouter
				.route(taskEvent.getValue().task.id).iterator().next(), null);

		// wait for all tasks to be processed
		// (again, since task processing is multithreaded to avoid blocking,
		// we may need to wait a little)
		ArgumentCaptor<ProcessEndSimEvent> endEvent = ArgumentCaptor
				.forClass(ProcessEndSimEvent.class);
		verify(processEventReceiver, timeout(5000)).receiveProcessEndEvent(
				endEvent.capture());
		assertEquals(endEvent.getValue().processInstance.processartifactid,
				processInstance.getId());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProcessDispatcherSignalProcessEndToUsers()
			throws InterruptedException {

		ActivitiProcessManager processManager = mock(ActivitiProcessManager.class);
		ITaskRouter taskRouter = mock(ITaskRouter.class);
		IProcessEventReceiver processEventReceiver = mock(IProcessEventReceiver.class);

		when(taskRouter.route(any(String.class))).thenReturn(
				new HashSet<String>(Arrays.asList("user1")));

		// automatically validate tasks
		ITaskValidator<Map<String, Object>, Map<String, Object>> taskValidator = mock(ITaskValidator.class);
		when(
				taskValidator.taskResultIsValid(anyString(), any(Map.class),
						any(Map.class))).thenReturn(true);

		final ActivitiProcessDispatcher dispatcher = new ActivitiProcessDispatcher(
				processInstanceData, processManager, processEventReceiver,
				processEngine.getTaskService(),
				processEngine.getRuntimeService(),
				processEngine.getRepositoryService(),
				processEngine.getHistoryService(), taskRouter, taskValidator,
				mock(BPMNExplorer.class));

		eventForwarder.setDispatcher(dispatcher);

		dispatcher.start();

		validateAllTasks(dispatcher, taskRouter, processEventReceiver);

		// check that the signal for process end is send to all the required
		// users

		ArgumentCaptor<ProcessEndSimEvent> endEvent = ArgumentCaptor
				.forClass(ProcessEndSimEvent.class);

		verify(processEventReceiver, timeout(5000)).receiveProcessEndEvent(
				endEvent.capture());
		assertEquals(endEvent.getValue().processInstance.processartifactid,
				processInstance.getId());

		assertTrue(endEvent.getValue().involvedusers.size() == TEST_PROCESS_USES
				.size());
		assertTrue(endEvent.getValue().involvedusers
				.containsAll(TEST_PROCESS_USES));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProcessDispatcherUnregister() throws InterruptedException {

		ActivitiProcessManager processManager = mock(ActivitiProcessManager.class);
		IProcessEventReceiver processEventReceiver = mock(IProcessEventReceiver.class);
		ITaskRouter taskRouter = mock(ITaskRouter.class);
		ITaskValidator<Map<String, Object>, Map<String, Object>> taskValidator = mock(ITaskValidator.class);

		when(taskRouter.route(any(String.class))).thenReturn(
				new HashSet<String>(Arrays.asList("user1")));

		// automatically validate tasks
		when(
				taskValidator.taskResultIsValid(anyString(), any(Map.class),
						any(Map.class))).thenReturn(true);

		final ActivitiProcessDispatcher dispatcher = new ActivitiProcessDispatcher(
				processInstanceData, processManager, processEventReceiver,
				processEngine.getTaskService(),
				processEngine.getRuntimeService(),
				processEngine.getRepositoryService(),
				processEngine.getHistoryService(), taskRouter, taskValidator,
				mock(BPMNExplorer.class));

		eventForwarder.setDispatcher(dispatcher);

		dispatcher.start();

		// reach end of process
		validateAllTasks(dispatcher, taskRouter, processEventReceiver);

		// should have unregistered itself from process manager at the end of
		// the process
		verify(processManager).signalProcessCompletion(
				eq(processInstance.getId()));

	}

	/**
	 * Helper method which create the set of roles involved in a task
	 *
	 * @param id
	 * @return the set of roles involved in a task
	 */
	private Set<String> getTaskRolesHelper(String id) {
		Set<String> roles = new HashSet<String>();

		// open the BPMN model of the process
		BpmnModel model = processEngine.getRepositoryService().getBpmnModel(
				processInstance.getProcessDefinitionId());

		for (FlowElement element : model.getMainProcess().getFlowElements()) {
			// filter to keep only user tasks
			if (element instanceof UserTask) {
				UserTask task = (UserTask) element;
				roles.addAll(task.getCandidateUsers());
				roles.addAll(task.getCandidateGroups());
			}
		}

		Set<String> result = new HashSet<String>();

		for (String role : roles) {
			if (!processEngine.getTaskService().createTaskQuery().taskId(id)
					.taskCandidateOrAssigned(role).list().isEmpty()
					|| !processEngine.getTaskService().createTaskQuery()
					.taskId(id).taskCandidateGroup(role).list()
					.isEmpty()) {
				result.add(role);
			}
		}

		return result;
	}

	/**
	 * Helper method to complete a process by validating all tasks
	 *
	 * @param dispatcher
	 * @param processEventReceiver
	 */
	private void validateAllTasks(final ActivitiProcessDispatcher dispatcher,
			final ITaskRouter taskRouter,
			IProcessEventReceiver processEventReceiver) {
		// we capture the task to respond
		ArgumentCaptor<TaskStartSimEvent> taskEvent = ArgumentCaptor
				.forClass(TaskStartSimEvent.class);
		verify(processEventReceiver, timeout(5000).times(1))
				.receiveTaskStartEvent(taskEvent.capture());
		assertEquals(taskEvent.getValue().task.processId,
				processInstance.getId());

		// automatically respond to next tasks dispatch
		doAnswer(new Answer<Void>() {
			public Void answer(InvocationOnMock invocation) throws Throwable {

				TaskStartSimEvent taskEvent = invocation.getArgumentAt(0,
						TaskStartSimEvent.class);

				dispatcher.submitTaskCompletion(taskEvent.task, taskRouter
						.route(taskEvent.task.id).iterator().next(), null);
				return null;
			}
		}).when(processEventReceiver).receiveTaskStartEvent(
				any(TaskStartSimEvent.class));

		// respond to first task dispatch
		dispatcher.submitTaskCompletion(taskEvent.getValue().task, taskRouter
				.route(taskEvent.getValue().task.id).iterator().next(), null);

		// wait for all tasks to be processed
		// (again, since task processing is multithreaded to avoid blocking,
		// we may need to wait a little)
		ArgumentCaptor<ProcessEndSimEvent> endEvent = ArgumentCaptor
				.forClass(ProcessEndSimEvent.class);
		verify(processEventReceiver, timeout(5000)).receiveProcessEndEvent(
				endEvent.capture());
		assertEquals(endEvent.getValue().processInstance.processartifactid,
				processInstance.getId());
	}

	// Helper class to emulate the message forwarding behavior of
	// ActivitiProcessManager
	private static class EventForwarder implements ActivitiEventListener {

		private final Executor jobHandler = Executors.newSingleThreadExecutor();

		private ActivitiProcessDispatcher dispatcher;

		public void setDispatcher(ActivitiProcessDispatcher dispatcher) {
			this.dispatcher = dispatcher;
		}

		@Override
		public void onEvent(final ActivitiEvent event) {
			if (this.dispatcher != null
					&& (event.getProcessInstanceId() != null)) {
				jobHandler.execute(new Runnable() {
					@Override
					public void run() {
						dispatcher.onEvent(event);
					}
				});
			}
		}

		@Override
		public boolean isFailOnException() {
			return false;
		}

	}
}
