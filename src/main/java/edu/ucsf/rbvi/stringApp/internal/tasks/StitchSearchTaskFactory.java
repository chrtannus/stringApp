package edu.ucsf.rbvi.stringApp.internal.tasks;

import java.awt.Dialog;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.apache.log4j.Logger;

import org.cytoscape.application.CyUserLog;
import org.cytoscape.application.swing.search.AbstractNetworkSearchTaskFactory;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.io.webservice.NetworkImportWebServiceClient;
import org.cytoscape.io.webservice.SearchWebServiceClient;
import org.cytoscape.io.webservice.swing.AbstractWebServiceGUIClient;

import edu.ucsf.rbvi.stringApp.internal.model.Databases;
import edu.ucsf.rbvi.stringApp.internal.model.StringManager;
import edu.ucsf.rbvi.stringApp.internal.model.StringNetwork;
import edu.ucsf.rbvi.stringApp.internal.tasks.GetAnnotationsTask;
import edu.ucsf.rbvi.stringApp.internal.tasks.ImportNetworkTaskFactory;
import edu.ucsf.rbvi.stringApp.internal.ui.GetTermsPanel;
import edu.ucsf.rbvi.stringApp.internal.ui.SearchOptionsPanel;
import edu.ucsf.rbvi.stringApp.internal.ui.SearchQueryComponent;
import edu.ucsf.rbvi.stringApp.internal.utils.ModelUtils;
import edu.ucsf.rbvi.stringApp.internal.utils.TextUtils;

public class StitchSearchTaskFactory extends AbstractNetworkSearchTaskFactory implements TaskObserver {
	StringManager manager;
	static String STITCH_ID = "edu.ucsf.rbvi.stitch";
	static String STITCH_URL = "http://stitch-db.org";
	static String STITCH_NAME = "STITCH compound query";
	static String STITCH_DESC = "Search STITCH for protein-compound interactions";
	static String STITCH_DESC_LONG = "<html>The compound query retrieves a STITCH network for one or more proteins or compounds. <br />"
										+ "STITCH is a resource to explore known and predicted interactions of chemicals and <br />"
										+ "proteins. Chemicals are linked to other chemicals and proteins by evidence derived <br />"
										+ "from experiments, databases and the literature. STITCH contains interactions for <br />"
										+ "between 300,000 small molecules and 2.6 million proteins from 1133 organisms.</html>";

	private StringNetwork stringNetwork = null;
	private SearchOptionsPanel optionsPanel = null;
	private SearchQueryComponent queryComponent = null;
	private final Logger logger = Logger.getLogger(CyUserLog.NAME);

	private static final Icon icon = new ImageIcon(
      StringSearchTaskFactory.class.getResource("/images/stitch_logo.png"));

	private static URL stitchURL() {
		try {
			return new URL(STITCH_URL);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public StitchSearchTaskFactory(StringManager manager) {
		super(STITCH_ID, STITCH_NAME, STITCH_DESC, icon, StitchSearchTaskFactory.stitchURL());
		this.manager = manager;
	}

	public boolean isReady() { 
		if (queryComponent.getQueryText() != null && queryComponent.getQueryText().length() > 0 && getTaxId() != -1)
			return true; 
		return false;
	}

	public TaskIterator createTaskIterator() {
		String terms = queryComponent.getQueryText();
		if (optionsPanel.getUseSmartDelimiters())
			terms = TextUtils.smartDelimit(terms);

		stringNetwork = new StringNetwork(manager);
		int taxon = getTaxId();
		return new TaskIterator(new GetAnnotationsTask(stringNetwork, taxon, terms, Databases.STITCH.getAPIName()));
	}

	@Override
	public String getName() { return STITCH_NAME; }

	@Override
	public String getId() { return STITCH_ID; }

	@Override
	public String getDescription() {
		return STITCH_DESC_LONG;
	}

	@Override
	public Icon getIcon() {
		return icon;
	}

	@Override
	public URL getWebsite() { 
		return StitchSearchTaskFactory.stitchURL();
	}

	// Create a JPanel that provides the species, confidence interval, and number of interactions
	// NOTE: we need to use reasonable defaults since it's likely the user won't actually change it...
	@Override
	public JComponent getOptionsComponent() {
		optionsPanel = new SearchOptionsPanel(manager);
		return optionsPanel;
	}

	@Override
	public JComponent getQueryComponent() {
		if (queryComponent == null)
			queryComponent = new SearchQueryComponent();
		return queryComponent;
	}

	@Override
	public TaskObserver getTaskObserver() { return this; }

	public int getTaxId() {
		try {
			if (optionsPanel.getSpecies() != null) {
				return optionsPanel.getSpecies().getTaxId();
			}
			return 9606; // Homo sapiens
		} catch (ClassCastException e) {
			// The user might not have given us a full species name
			String name = optionsPanel.getSpeciesText();
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JOptionPane.showMessageDialog(null, "Unknown species: '"+name+"'",
								                        "Unknown species", JOptionPane.ERROR_MESSAGE); 
				}
			});
			return -1;
		}
	}

	public String getSpecies() {
		// This will eventually come from the OptionsComponent...
		if (optionsPanel.getSpecies() != null)
			return optionsPanel.getSpecies().toString();
		return "Homo sapiens"; // Homo sapiens
	}

	public int getAdditionalNodes() {
		// This will eventually come from the OptionsComponent...
		return optionsPanel.getAdditionalNodes();
	}

	public int getConfidence() {
		// This will eventually come from the OptionsComponent...
		return optionsPanel.getConfidence();
	}

	@Override
	public void allFinished(FinishStatus finishStatus) {
	}

	@Override
	public void taskFinished(ObservableTask task) {
		if (!(task instanceof GetAnnotationsTask)) {
			return;
		}
		GetAnnotationsTask annTask = (GetAnnotationsTask)task;

		final int taxon = annTask.getTaxon();
		if (stringNetwork.getAnnotations() == null || stringNetwork.getAnnotations().size() == 0) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JOptionPane.showMessageDialog(null, "Your query returned no results",
								                        "No results", JOptionPane.ERROR_MESSAGE); 
				}
			});
			return;
		}
		boolean noAmbiguity = stringNetwork.resolveAnnotations();
		if (noAmbiguity) {
			int additionalNodes = getAdditionalNodes();
			// This mimics the String web site behavior
			if (stringNetwork.getResolvedTerms() == 1 && additionalNodes == 0) {
				/*
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						JOptionPane.showMessageDialog(null, 
												"This will return only one node (Hint: increase maximum interactors slider?)",
									       "Hint", JOptionPane.WARNING_MESSAGE); 
					}
				});
				*/
				additionalNodes = 10;
				logger.warn("STRING Compound: Only one protein or compound was selected -- additional interactions set to 10");
			}
			//	additionalNodes = 10;

			final int addNodes = additionalNodes;

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					importNetwork(taxon, getConfidence(), addNodes);
				}
			});
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JDialog d = new JDialog();
					d.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
					d.setTitle("Resolve Ambiguous Terms");
					GetTermsPanel panel = new GetTermsPanel(manager, stringNetwork, Databases.STITCH.getAPIName(), 
					                                        getSpecies(), false, getConfidence(), getAdditionalNodes());
					panel.createResolutionPanel();
					d.setContentPane(panel);
					d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
					d.pack();
					d.setVisible(true);
				}
			});
		}
	}

	void importNetwork(int taxon, int confidence, int additionalNodes) {
		Map<String, String> queryTermMap = new HashMap<>();
		List<String> stringIds = stringNetwork.combineIds(queryTermMap);
		// System.out.println("Importing "+stringIds);
		TaskFactory factory = new ImportNetworkTaskFactory(stringNetwork, getSpecies(), 
		                                                   taxon, confidence, additionalNodes, stringIds,
																											 queryTermMap, Databases.STITCH.getAPIName());
		manager.execute(factory.createTaskIterator());
	}

}
