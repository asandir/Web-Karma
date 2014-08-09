/**
 * *****************************************************************************
 * Copyright 2012 University of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * This code was developed by the Information Integration Group as part of the
 * Karma project at the Information Sciences Institute of the University of
 * Southern California. For more information, publications, and related
 * projects, please see: http://www.isi.edu/integration
 *****************************************************************************
 */
package edu.isi.karma.webserver;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.IPreviewable;
import edu.isi.karma.controller.update.ErrorUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.view.VWorkspaceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RequestController extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(RequestController.class);

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		// initilaize the JETTY_PORT for the first request
		if (ServletContextParameterMap.getParameterValue(ServletContextParameterMap.ContextParameter.JETTY_PORT).isEmpty()) {
			String port = String.valueOf(request.getServerPort());
			String protocol = request.getProtocol().split("/")[0];
			String host = protocol.toLowerCase() + "://" + request.getServerName();

			ServletContextParameterMap.setParameterValue(ServletContextParameterMap.ContextParameter.JETTY_PORT, port);
			logger.info("JETTY_PORT initilized to " + port);


			ServletContextParameterMap.setParameterValue(ServletContextParameterMap.ContextParameter.JETTY_HOST, host);
			logger.info("JETTY_HOST initilized to " + host);

			// also set PUBLIC_RDF_ADDRESS
			ServletContextParameterMap.setParameterValue(ServletContextParameterMap.ContextParameter.PUBLIC_RDF_ADDRESS,
					host + ":" + port + "/RDF/");

			// also set CLEANING_SERVICE_URL
			ServletContextParameterMap.setParameterValue(ServletContextParameterMap.ContextParameter.CLEANING_SERVICE_URL,
					host + ":" + port
					+ ServletContextParameterMap.getParameterValue(ServletContextParameterMap.ContextParameter.CLEANING_SERVICE_URL));

			ServletContextParameterMap.setParameterValue(ServletContextParameterMap.ContextParameter.CLUSTER_SERVICE_URL,
					host + ":" + port
					+ ServletContextParameterMap.getParameterValue(ServletContextParameterMap.ContextParameter.CLUSTER_SERVICE_URL));
		}

		String workspaceId = request.getParameter("workspaceId");
		ExecutionController ctrl = WorkspaceRegistry.getInstance().getExecutionController(workspaceId);
		if (ctrl == null) {
			logger.debug("No execution controller found. This sometime happens when the server is restarted and "
					+ "an already open window is refereshed (and is okay to happen). A command is sent to the server "
					+ "to destroy all workspace objects.");
			return;
		}

		VWorkspace vWorkspace = VWorkspaceRegistry.getInstance().getVWorkspace(workspaceId);
		String responseString = "";
		boolean isPreview = Boolean.parseBoolean(request.getParameter("isPreview"));
		boolean isUserInteraction = Boolean.parseBoolean(request.getParameter("isUserInteraction"));
		boolean isExecute = Boolean.parseBoolean(request.getParameter("execute"));
		if (isUserInteraction) {
			String commandId = request.getParameter("commandId");
			Command currentCommand = (Command) ctrl.getWorkspace().getCommandHistory().getPreviewCommand(commandId);
			try {
				UpdateContainer updateContainer;
				if (!isExecute)
					updateContainer = ((IPreviewable) currentCommand).handleUserActions(request);
				else {
					((IPreviewable) currentCommand).handleUserActions(request);
					updateContainer = ctrl.invokeCommand(currentCommand);
					ctrl.getWorkspace().getCommandHistory().removePreviewCommand(currentCommand);
				}
				updateContainer.applyUpdates(vWorkspace);
				responseString = updateContainer.generateJson(vWorkspace);
			} catch (Exception e) {
				responseString = getErrorMessage(vWorkspace, e);
			}
		}
		else if (isPreview) {
			Command command = ctrl.getCommand(request);
			try {
				UpdateContainer updateContainer = ((IPreviewable) command).showPreview();
				ctrl.getWorkspace().getCommandHistory().addPreviewCommand(command);
				updateContainer.applyUpdates(vWorkspace);
				responseString = updateContainer.generateJson(vWorkspace);
			} catch (CommandException e) {
				responseString = getErrorMessage(vWorkspace, e);
			}

		}
		else {
			Command command = ctrl.getCommand(request);
			try {
				UpdateContainer updateContainer =ctrl.invokeCommand(command);
				updateContainer.applyUpdates(vWorkspace);
				responseString = updateContainer.generateJson(vWorkspace);
			} catch(Exception e) {
				responseString = getErrorMessage(vWorkspace, e);
			}
		}

		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(responseString);
		response.flushBuffer();
	}

	private String getErrorMessage(VWorkspace vWorkspace, Throwable e) {
		e.printStackTrace();
		UpdateContainer updateContainer = new UpdateContainer();
		updateContainer.add(new ErrorUpdate("Error:" + e.getMessage()));
		return updateContainer.generateJson(vWorkspace);
	}
}