package org.waterforpeople.mapping.portal.client.widgets.component;

import java.util.HashMap;
import java.util.Map;

import org.waterforpeople.mapping.app.gwt.client.util.TextConstants;

import com.gallatinsystems.framework.gwt.util.client.CompletionListener;
import com.gallatinsystems.framework.gwt.util.client.WidgetDialog;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Dialog box that can be used to construct an AccessPointSearchCriteria object
 * 
 * 
 * @author Christopher Fagiani
 * 
 */
public class AccessPointFilterDialog extends WidgetDialog implements
		ClickHandler {
	private static TextConstants TEXT_CONSTANTS = GWT
			.create(TextConstants.class);
	public static final String CRITERIA_KEY = "APcriteria";
	private AccessPointSearchControl searchControl;
	private Button okButton;
	private Button cancelButton;

	public AccessPointFilterDialog(CompletionListener listener) {
		super(TEXT_CONSTANTS.accessPointFilterDialogTitle(), null, true, listener);
		Panel panel = new VerticalPanel();
		searchControl = new AccessPointSearchControl();
		panel.add(searchControl);
		Panel buttonPanel = new HorizontalPanel();
		okButton = new Button(TEXT_CONSTANTS.ok());
		okButton.addClickHandler(this);
		cancelButton = new Button(TEXT_CONSTANTS.cancel());
		cancelButton.addClickHandler(this);
		buttonPanel.add(okButton);
		buttonPanel.add(cancelButton);
		panel.add(buttonPanel);
		setContentWidget(panel);
	}

	@Override
	public void onClick(ClickEvent event) {
		if (event.getSource() == cancelButton) {
			hide(true);
		} else if (event.getSource() == okButton) {
			hide(true);
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put(CRITERIA_KEY, searchControl.getSearchCriteria());
			notifyListener(true, payload);
		}
	}
}
