package edu.ucsf.rbvi.stringApp.internal.tasks;

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
import edu.ucsf.rbvi.stringApp.internal.model.Species;
import edu.ucsf.rbvi.stringApp.internal.model.StringManager;
import edu.ucsf.rbvi.stringApp.internal.model.StringNetwork;
import edu.ucsf.rbvi.stringApp.internal.tasks.GetAnnotationsTask;
import edu.ucsf.rbvi.stringApp.internal.tasks.ImportNetworkTaskFactory;
import edu.ucsf.rbvi.stringApp.internal.ui.PubMedQueryPanel;
import edu.ucsf.rbvi.stringApp.internal.ui.SearchOptionsPanel;

public class PubmedSearchTaskFactory extends AbstractNetworkSearchTaskFactory {
	StringManager manager;
	static String PUBMED_ID = "edu.ucsf.rbvi.pubmed";
	static String PUBMED_URL = "http://string-db.org";
	static String PUBMED_NAME = "STRING PubMed query";
	static String PUBMED_DESC = "Search STRING for protein-protein interactions based on PubMed queries";
	static String PUBMED_DESC_LONG =  "<html>The PubMed query retrieves a STRING network pertaining to any topic of interest <br />"
											+ "based on text mining of PubMed abstracts. STRING is a database of known and <br />"
											+ "predicted protein interactions for thousands of organisms, which are integrated <br />"
											+ "from several sources, scored, and transferred across orthologs. The network <br />"
											+ "includes both physical interactions and functional associations.</html>";

	private StringNetwork stringNetwork = null;
	private SearchOptionsPanel optionsPanel = null;

	private static final Icon icon = new ImageIcon(
      StringSearchTaskFactory.class.getResource("/images/pubmed_logo.png"));

	private static URL stringURL() {
		try {
			return new URL(PUBMED_URL);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public PubmedSearchTaskFactory(StringManager manager) {
		super(PUBMED_ID, PUBMED_NAME, PUBMED_DESC, icon, PubmedSearchTaskFactory.stringURL());
		this.manager = manager;
	}

	public boolean isReady() { return true; }

	public TaskIterator createTaskIterator() {
		final String terms = getQuery();

		if (terms == null) {
			throw new NullPointerException("Query string is null.");
		}

		return new TaskIterator(new AbstractTask() {
			@Override
			public void run(TaskMonitor m) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run () {
						Species species;
						try {
							species = optionsPanel.getSpecies();
						} catch (ClassCastException e) {
							String speciesText = optionsPanel.getSpeciesText();
							m.showMessage(TaskMonitor.Level.ERROR, "Unknown species: '"+speciesText+"'");
							return;
						}
						JDialog d = new JDialog();
						d.setTitle("Resolve Ambiguous Terms");
						PubMedQueryPanel panel = new PubMedQueryPanel(manager, stringNetwork, terms, 
						                                              species,
						                                              optionsPanel.getConfidence(),
						                                              optionsPanel.getAdditionalNodes());
						d.setContentPane(panel);
						d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
						d.pack();
						d.setVisible(true);
						panel.doImport();
					}
				});
			}
		});

	}

	@Override
	public String getName() { return PUBMED_NAME; }

	@Override
	public String getId() { return PUBMED_ID; }

	@Override
	public String getDescription() {
		return PUBMED_DESC_LONG;
	}

	@Override
	public Icon getIcon() {
		return icon;
	}

	@Override
	public URL getWebsite() { 
		return PubmedSearchTaskFactory.stringURL();
	}

	// Create a JPanel that provides the species, confidence interval, and number of interactions
	// NOTE: we need to use reasonable defaults since it's likely the user won't actually change it...
	@Override
	public JComponent getOptionsComponent() {
		optionsPanel = new SearchOptionsPanel(manager, true, false);
		return optionsPanel;
	}

	@Override
	public JComponent getQueryComponent() {
		return null;
	}

	@Override
	public TaskObserver getTaskObserver() { return null; }

}
