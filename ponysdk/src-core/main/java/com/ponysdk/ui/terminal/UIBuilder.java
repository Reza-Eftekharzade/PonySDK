/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
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
 */

package com.ponysdk.ui.terminal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.ponysdk.ui.terminal.event.CommunicationErrorEvent;
import com.ponysdk.ui.terminal.event.HttpRequestSendEvent;
import com.ponysdk.ui.terminal.event.HttpResponseReceivedEvent;
import com.ponysdk.ui.terminal.exception.ServerException;
import com.ponysdk.ui.terminal.instruction.PTInstruction;
import com.ponysdk.ui.terminal.model.BinaryModel;
import com.ponysdk.ui.terminal.model.ClientToServerModel;
import com.ponysdk.ui.terminal.model.HandlerModel;
import com.ponysdk.ui.terminal.model.ReaderBuffer;
import com.ponysdk.ui.terminal.model.ServerToClientModel;
import com.ponysdk.ui.terminal.request.RequestBuilder;
import com.ponysdk.ui.terminal.ui.PTCookies;
import com.ponysdk.ui.terminal.ui.PTObject;
import com.ponysdk.ui.terminal.ui.PTStreamResource;
import com.ponysdk.ui.terminal.ui.PTWindow;
import com.ponysdk.ui.terminal.ui.PTWindowManager;

public class UIBuilder implements ValueChangeHandler<String>, UIService, HttpResponseReceivedEvent.Handler,
        HttpRequestSendEvent.Handler {

    private final static Logger log = Logger.getLogger(UIBuilder.class.getName());

    private static EventBus rootEventBus = new SimpleEventBus();

    private final UIFactory uiFactory = new UIFactory();
    private final Map<Integer, PTObject> objectByID = new HashMap<>();
    private final Map<UIObject, Integer> objectIDByWidget = new HashMap<>();
    private final Map<Integer, UIObject> widgetIDByObjectID = new HashMap<>();
    private final List<PTInstruction> stackedInstructions = new ArrayList<>();
    private final List<JSONObject> stackedErrors = new ArrayList<>();

    // private final Map<Integer, JSONObject> incomingMessageQueue = new HashMap<>();

    private SimplePanel loadingMessageBox;
    private PopupPanel communicationErrorMessagePanel;
    private Timer timer;
    private int numberOfrequestInProgress = 0;

    private boolean updateMode;
    private boolean pendingClose;

    private RequestBuilder requestBuilder;

    private long lastReceived = -1;

    public static int sessionID;

    private CommunicationErrorHandler communicationErrorHandler;
    private final Map<String, JavascriptAddOnFactory> javascriptAddOnFactories = new HashMap<>();

    private final Map<Integer, List<PTInstruction>> instructionsByObjectID = new HashMap<>();

    public UIBuilder() {
        History.addValueChangeHandler(this);

        rootEventBus.addHandler(HttpResponseReceivedEvent.TYPE, this);
        rootEventBus.addHandler(HttpRequestSendEvent.TYPE, this);
    }

    public void init(final int ID, final RequestBuilder requestBuilder) {
        if (log.isLoggable(Level.INFO))
            log.info("Init request builder");

        UIBuilder.sessionID = ID;
        this.requestBuilder = requestBuilder;

        loadingMessageBox = new SimplePanel();

        communicationErrorMessagePanel = new PopupPanel(false, true);
        communicationErrorMessagePanel.setGlassEnabled(true);
        communicationErrorMessagePanel.setStyleName("pony-notification");
        communicationErrorMessagePanel.addStyleName("error");

        RootPanel.get().add(loadingMessageBox);

        loadingMessageBox.setStyleName("pony-LoadingMessageBox");
        loadingMessageBox.getElement().getStyle().setVisibility(Visibility.HIDDEN);
        loadingMessageBox.getElement().setInnerText("Loading ...");

        final PTCookies cookies = new PTCookies();
        objectByID.put(0, cookies);

        // hide loading component
        final Widget w = RootPanel.get("loading");
        if (w != null) {
            w.setSize("0px", "0px");
            w.setVisible(false);
        } else {
            log.log(Level.WARNING, "Include splash screen html element into your index.html with id=\"loading\"");
        }
    }

    @Override
    public void onCommunicationError(final Throwable exception) {
        rootEventBus.fireEvent(new CommunicationErrorEvent(exception));

        if (pendingClose)
            return;

        if (loadingMessageBox == null) {
            // First load failed
            if (exception instanceof StatusCodeException) {
                final StatusCodeException codeException = (StatusCodeException) exception;
                if (codeException.getStatusCode() == 0)
                    return;
            }
            log.log(Level.SEVERE, "Cannot inititialize the application : " + exception.getMessage() + "\n" + exception
                    + "\nPlease reload your application", exception);
            return;
        }

        if (communicationErrorHandler != null) {
            if (exception instanceof StatusCodeException) {
                final StatusCodeException statusCodeException = (StatusCodeException) exception;
                communicationErrorHandler.onCommunicationError("" + statusCodeException.getStatusCode(),
                        statusCodeException.getMessage());
            } else {
                communicationErrorHandler.onCommunicationError("x", exception.getMessage());
            }
        } else {
            if (exception instanceof StatusCodeException) {
                final StatusCodeException statusCodeException = (StatusCodeException) exception;
                showCommunicationErrorMessage(statusCodeException);
            } else {
                log.log(Level.SEVERE,
                        "An unexcepted error occured: " + exception.getMessage() + ". Please check the server logs.",
                        exception);
            }
        }
    }

    public void updateMainTerminal(final ReaderBuffer buffer) {
        while (buffer.hasRemaining()) {
            // Detect if the message is not for the main terminal but for a specific window
            final BinaryModel windowIdModel = buffer.getBinaryModel();
            if (ServerToClientModel.WINDOW_ID.equals(windowIdModel.getModel())) {
                // Event on a specific window
                final int requestedWindowId = windowIdModel.getIntValue();
                // Main terminal, we need to dispatch the event
                final PTWindow window = PTWindowManager.getWindow(requestedWindowId);
                if (window != null) {
                    log.log(Level.INFO, "The main terminal send the buffer to window " + requestedWindowId);
                    window.postMessage(buffer);
                } else {
                    log.log(Level.SEVERE, "The requested window " + requestedWindowId + " doesn't exist");

                    // FIXME To be remove
                    update(buffer);
                }
            } else {
                buffer.rewind(windowIdModel);
                update(buffer);
            }
        }
    }

    public void update(final ReaderBuffer buffer) {
        if (buffer.hasRemaining()) {
            final BinaryModel binaryModel = buffer.getBinaryModel();

            if (ServerToClientModel.TYPE_CREATE.equals(binaryModel.getModel())) {
                final PTObject ptObject = processCreate(buffer, binaryModel.getIntValue());
                processUpdate(buffer, ptObject);
            } else if (ServerToClientModel.TYPE_UPDATE.equals(binaryModel.getModel())) {
                processUpdate(buffer, getPTObject(binaryModel.getIntValue()));
            } else if (ServerToClientModel.TYPE_ADD.equals(binaryModel.getModel())) {
                processAdd(buffer, getPTObject(binaryModel.getIntValue()));
            } else if (ServerToClientModel.TYPE_GC.equals(binaryModel.getModel())) {
                processGC(binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_REMOVE.equals(binaryModel.getModel())) {
                processRemove(buffer, binaryModel.getIntValue());
            } else if (ServerToClientModel.TYPE_ADD_HANDLER.equals(binaryModel.getModel())) {
                processAddHandler(buffer, HandlerModel.values()[binaryModel.getByteValue()]);
            } else if (ServerToClientModel.TYPE_REMOVE_HANDLER.equals(binaryModel.getModel())) {
                processRemoveHandler(buffer, getPTObject(binaryModel.getIntValue()));
            } else if (ServerToClientModel.TYPE_HISTORY.equals(binaryModel.getModel())) {
                processHistory(buffer, binaryModel.getStringValue());
            } else if (ServerToClientModel.TYPE_CLOSE.equals(binaryModel.getModel())) {
                processClose(buffer);
            } else {
                log.log(Level.WARNING, "Unknown instruction type : " + binaryModel.getModel());
            }
        }
    }

    @Override
    public void update(final JSONValue data) {
        JSONArray jsonArray = data.isArray();
        if (jsonArray == null)
            jsonArray = data.isObject().get(ClientToServerModel.APPLICATION_INSTRUCTIONS.toStringValue()).isArray();

        for (int i = 0; i < jsonArray.size(); i++) {
            final PTInstruction instruction = new PTInstruction(jsonArray.get(i).isObject().getJavaScriptObject());
            executeInstruction(instruction);
        }
    }

    @Override
    public void stackError(final PTInstruction currentInstruction, final Throwable e) {
        String msg;
        if (e.getMessage() == null)
            msg = "NA";
        else
            msg = e.getMessage();

        final JSONObject jsoObject = new JSONObject();
        jsoObject.put("message",
                new JSONString("PonySDK has encountered an internal error on instruction : " + currentInstruction));
        jsoObject.put("details", new JSONString(msg));
        stackedErrors.add(jsoObject);
    }

    private PTObject processCreate(final ReaderBuffer buffer, final int objectIdValue) {
        // ServerToClientModel.WIDGET_TYPE
        final WidgetType widgetType = WidgetType.values()[buffer.getBinaryModel().getByteValue()];

        final PTObject ptObject = uiFactory.newUIObject(this, widgetType);
        if (log.isLoggable(Level.FINE))
            log.log(Level.FINE, "Create " + ptObject.getClass().getSimpleName() + " #" + objectIdValue);
        if (ptObject != null) {
            ptObject.create(buffer, objectIdValue, this);
            objectByID.put(objectIdValue, ptObject);
        } else {
            log.warning("Cannot create object " + objectIdValue + " with widget type : " + widgetType);
        }

        return ptObject;
    }

    private void processAdd(final ReaderBuffer buffer, final PTObject ptObject) {
        // ServerToClientModel.PARENT_OBJECT_ID
        final int parentId = buffer.getBinaryModel().getIntValue();
        final PTObject parentObject = getPTObject(parentId);
        if (parentObject != null) {
            if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "Add " + ptObject + " on " + parentObject);
            parentObject.add(buffer, ptObject);
        } else {
            log.warning("Cannot add object " + ptObject + " to an garbaged parent object #" + parentId);
        }
    }

    private void processUpdate(final ReaderBuffer buffer, final PTObject ptObject) {
        BinaryModel binaryModel;
        boolean result = false;
        do {
            binaryModel = buffer.getBinaryModel();
            if (!BinaryModel.NULL.equals(binaryModel)) {
                if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "Update : " + binaryModel + " on " + ptObject);
                result = ptObject.update(buffer, binaryModel);
            }
        } while (result && buffer.hasRemaining());

        if (!result) buffer.rewind(binaryModel);
    }

    private void processRemove(final ReaderBuffer buffer, final int objectId) {
        final int parentId = buffer.getBinaryModel().getIntValue();

        final PTObject ptObject = getPTObject(objectId);
        PTObject parentObject;
        if (parentId == -1)
            parentObject = ptObject;
        else
            parentObject = getPTObject(parentId);

        if (parentObject != null) {
            if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "Remove : " + ptObject);
            parentObject.remove(buffer, ptObject, this);
        } else
            log.warning("Cannot remove a garbaged object #" + objectId);
    }

    private void processAddHandler(final ReaderBuffer buffer, final HandlerModel handlerModel) {
        if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "Add handler " + handlerModel);
        if (HandlerModel.HANDLER_STREAM_REQUEST_HANDLER.equals(handlerModel)) {
            new PTStreamResource().addHandler(buffer, handlerModel, this);
        } else {
            // ServerToClientModel.OBJECT_ID
            final int id = buffer.getBinaryModel().getIntValue();
            final PTObject ptObject = getPTObject(id);
            if (ptObject != null)
                ptObject.addHandler(buffer, handlerModel, this);
            else
                log.warning("Cannot add handler on a garbaged object #" + id);
        }
    }

    private void processRemoveHandler(final ReaderBuffer buffer, final PTObject ptObject) {
        if (ptObject != null) {
            if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "Remove handler : " + ptObject);
            ptObject.removeHandler(buffer, this);
        }
    }

    private void processHistory(final ReaderBuffer buffer, final String token) {
        if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "History instruction : " + token);
        final String oldToken = History.getToken();

        // ServerToClientModel.HISTORY_FIRE_EVENTS
        final boolean fireEvents = buffer.getBinaryModel().getBooleanValue();
        if (oldToken != null && oldToken.equals(token)) {
            if (fireEvents)
                History.fireCurrentHistoryState();
        } else {
            History.newItem(token, fireEvents);
        }
    }

    private void processClose(final ReaderBuffer buffer) {
        pendingClose = true;
        sendDataToServer(buffer);

        // TODO nciaravola no need

        Scheduler.get().scheduleDeferred(new ScheduledCommand() {

            @Override
            public void execute() {
                PonySDK.reload();
            }
        });
    }

    private void processGC(final int objectId) {
        final PTObject ptObject = unRegisterObject(objectId);
        if (ptObject != null)
            ptObject.gc(this);
        else
            log.warning("Cannot GC a garbaged object #" + objectId);
    }

    /**
     * Stack instruction until window information is not done
     *
     * @param instruction
     */
    private void stackInstruction(final PTInstruction instruction) {
        List<PTInstruction> instructions = instructionsByObjectID.get(instruction.getObjectID());
        if (instructions == null) {
            instructions = new ArrayList<>();

            if (log.isLoggable(Level.FINE))
                log.log(Level.FINE, "Stack Instruction : " + instruction);

            instructionsByObjectID.put(instruction.getObjectID(), instructions);
        }

        instructions.add(instruction); // wait window information
    }

    protected void updateIncomingSeqNum(final long receivedSeqNum) {
        final long previous = lastReceived;
        if (previous + 1 != receivedSeqNum)
            log.log(Level.SEVERE,
                    "Wrong seqnum received. Expecting #" + (previous + 1) + " but received #" + receivedSeqNum);
        lastReceived = receivedSeqNum;
    }

    @Override
    public PTObject unRegisterObject(final int objectId) {
        final PTObject ptObject = objectByID.remove(objectId);
        final UIObject uiObject = widgetIDByObjectID.remove(objectId);
        if (uiObject != null)
            objectIDByWidget.remove(uiObject);
        return ptObject;
    }

    @Override
    public void stackInstrution(final PTInstruction instruction) {
        if (!updateMode)
            sendDataToServer(instruction);
        else
            stackedInstructions.add(instruction);
    }

    @Override
    public void flushEvents() {
        if (stackedInstructions.isEmpty())
            return;

        sendDataToServer(stackedInstructions);

        stackedInstructions.clear();
    }

    @Override
    public void sendDataToServer(final Widget widget, final PTInstruction instruction) {
        if (log.isLoggable(Level.FINE)) {
            if (widget != null) {
                final Element source = widget.getElement();
                if (source != null)
                    log.fine("Action triggered, Instruction [" + instruction + "] , " + source.getInnerHTML());
            }
        }
        sendDataToServer(instruction);
    }

    public void sendDataToServer(final List<PTInstruction> instructions) {
        final JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < instructions.size(); i++) {
            // TODO Weird set, always 0 ???
            jsonArray.set(0, instructions.get(0));
        }

        sendDataToServer(jsonArray);
    }

    @Override
    public void sendDataToServer(final PTInstruction instruction) {
        final JSONArray jsonArray = new JSONArray();
        jsonArray.set(0, instruction);

        sendDataToServer(jsonArray);
    }

    @Override
    public void sendDataToServer(final ReaderBuffer buffer) {
    }

    public void sendDataToServer(final JSONArray jsonArray) {
        final PTInstruction requestData = new PTInstruction();
        requestData.put(ClientToServerModel.APPLICATION_VIEW_ID, sessionID);
        requestData.put(ClientToServerModel.APPLICATION_INSTRUCTIONS, jsonArray);

        if (!stackedErrors.isEmpty()) {
            final JSONArray errors = new JSONArray();
            int i = 0;
            for (final JSONObject jsoObject : stackedErrors)
                errors.set(i++, jsoObject);
            stackedErrors.clear();
            requestData.put(ClientToServerModel.APPLICATION_ERRORS, errors);
        }

        if (log.isLoggable(Level.FINE))
            log.log(Level.FINE, "Data to send " + requestData.toString());

        requestBuilder.send(requestData.toString());
    }

    private Timer scheduleLoadingMessageBox() {

        if (loadingMessageBox == null)
            return null;

        final Timer timer = new Timer() {

            @Override
            public void run() {
                loadingMessageBox.getElement().getStyle().setVisibility(Visibility.VISIBLE);
            }
        };
        timer.schedule(500);
        return timer;
    }

    private void showCommunicationErrorMessage(final StatusCodeException caught) {

        final VerticalPanel content = new VerticalPanel();
        final HorizontalPanel actionPanel = new HorizontalPanel();
        actionPanel.setSize("100%", "100%");

        if (caught.getStatusCode() == ServerException.INVALID_SESSION) {
            content.add(new HTML("Server connection failed <br/>Code : " + caught.getStatusCode() + "<br/>" + "Cause : "
                    + caught.getMessage()));

            final Anchor reloadAnchor = new Anchor("reload");
            reloadAnchor.addClickHandler(new ClickHandler() {

                @Override
                public void onClick(final ClickEvent event) {
                    History.newItem("");
                    PonySDK.reload();
                }
            });

            actionPanel.add(reloadAnchor);
            actionPanel.setCellHorizontalAlignment(reloadAnchor, HasHorizontalAlignment.ALIGN_CENTER);
            actionPanel.setCellVerticalAlignment(reloadAnchor, HasVerticalAlignment.ALIGN_MIDDLE);
        } else {
            content.add(new HTML("An unexpected error occured <br/>Code : " + caught.getStatusCode() + "<br/>"
                    + "Cause : " + caught.getMessage()));
        }

        final Anchor closeAnchor = new Anchor("close");
        closeAnchor.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(final ClickEvent event) {
                communicationErrorMessagePanel.hide();
            }
        });
        actionPanel.add(closeAnchor);
        actionPanel.setCellHorizontalAlignment(closeAnchor, HasHorizontalAlignment.ALIGN_CENTER);
        actionPanel.setCellVerticalAlignment(closeAnchor, HasVerticalAlignment.ALIGN_MIDDLE);

        content.add(actionPanel);

        communicationErrorMessagePanel.setWidget(content);
        communicationErrorMessagePanel.setPopupPositionAndShow(new PositionCallback() {

            @Override
            public void setPosition(final int offsetWidth, final int offsetHeight) {
                communicationErrorMessagePanel.setPopupPosition((Window.getClientWidth() - offsetWidth) / 2,
                        (Window.getClientHeight() - offsetHeight) / 2);
            }
        });
    }

    @Override
    public void onValueChange(final ValueChangeEvent<String> event) {
        if (event.getValue() != null && !event.getValue().isEmpty()) {
            final PTInstruction eventInstruction = new PTInstruction();
            eventInstruction.put(ClientToServerModel.TYPE_HISTORY, event.getValue());
            stackInstrution(eventInstruction);
        }
    }

    @Override
    public PTObject getPTObject(final int id) {
        final PTObject ptObject = objectByID.get(id);
        if (ptObject == null) log.warning("PTObject #" + id + " not found");
        return ptObject;
    }

    @Override
    public PTObject getPTObject(final UIObject uiObject) {
        final Integer objectID = objectIDByWidget.get(uiObject);
        if (objectID != null)
            return getPTObject(objectID);
        return null;
    }

    @Override
    public void registerUIObject(final int ID, final UIObject uiObject) {
        objectIDByWidget.put(uiObject, ID);
        widgetIDByObjectID.put(ID, uiObject);
    }

    @Override
    public void onHttpRequestSend(final HttpRequestSendEvent event) {
        numberOfrequestInProgress++;

        if (timer == null)
            timer = scheduleLoadingMessageBox();
    }

    @Override
    public void onHttpResponseReceivedEvent(final HttpResponseReceivedEvent event) {
        if (numberOfrequestInProgress > 0)
            numberOfrequestInProgress--;

        hideLoadingMessageBox();
    }

    private void hideLoadingMessageBox() {

        if (loadingMessageBox == null)
            return;

        if (numberOfrequestInProgress < 1 && timer != null) {
            timer.cancel();
            timer = null;
            loadingMessageBox.getElement().getStyle().setVisibility(Visibility.HIDDEN);
        }
    }

    public void executeInstruction(final JavaScriptObject jso) {
        update(new JSONObject(jso));
    }

    public static EventBus getRootEventBus() {
        return rootEventBus;
    }

    public void registerCommunicationError(final CommunicationErrorHandler communicationErrorClosure) {
        this.communicationErrorHandler = communicationErrorClosure;
    }

    public void registerJavascriptAddOnFactory(final String signature,
            final JavascriptAddOnFactory javascriptAddOnFactory) {
        this.javascriptAddOnFactories.put(signature, javascriptAddOnFactory);
    }

    @Override
    public Map<String, JavascriptAddOnFactory> getJavascriptAddOnFactory() {
        return javascriptAddOnFactories;
    }

    // FIXME REMOVE
    @Deprecated
    @Override
    public void processInstruction(final PTInstruction instruction) throws Exception {
        log.severe("Deprecated UIBuilder#processInstruction() method, don't use it : " + instruction);
    }

    // FIXME REMOVE
    @Deprecated
    private void executeInstruction(final PTInstruction instruction) {
        log.severe("Deprecated UIBuilder#executeInstruction() method, don't use it : " + instruction);
    }

}
