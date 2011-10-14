/**************************************************************************************************
 * Copyright (c) 2010 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 *************************************************************************************************/
package de.uni_koeln.ub.drc.ui.views;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISaveablePart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.part.ViewPart;

import scala.collection.mutable.Stack;

import com.quui.sinist.XmlDb;

import de.uni_koeln.ub.drc.data.Index;
import de.uni_koeln.ub.drc.data.Modification;
import de.uni_koeln.ub.drc.data.Page;
import de.uni_koeln.ub.drc.data.User;
import de.uni_koeln.ub.drc.data.Word;
import de.uni_koeln.ub.drc.ui.DrcUiActivator;
import de.uni_koeln.ub.drc.ui.Messages;
import de.uni_koeln.ub.drc.ui.facades.IDialogConstantsHelper;
import de.uni_koeln.ub.drc.util.PlainTextCopy;

/**
 * A view that the area to edit the text. Marks the section in the image file
 * that corresponds to the word in focus (in {@link CheckView}).
 * 
 * @author Fabian Steeg (fsteeg)
 */
public final class EditView extends ViewPart implements ISaveablePart {

	/**
	 * The class / EditView ID
	 */
	public static final String ID = EditView.class.getName().toLowerCase();

	// @Inject
	// IEclipseContext context;
	// @Inject
	// private IEventBroker eventBroker;
	// final MDirtyable dirtyable;

	static final String SAVED = "pagesaved"; //$NON-NLS-1$
	EditComposite editComposite;
	Label label;
	ScrolledComposite sc;
	private boolean dirtyable = false;

	@Override
	public void createPartControl(final Composite parent) {
		// this.dirtyable = dirtyable;
		sc = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
		label = new Label(parent, SWT.CENTER | SWT.WRAP);
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		editComposite = new EditComposite(this, SWT.NONE);
		sc.setContent(editComposite);
		sc.setExpandVertical(true);
		sc.setExpandHorizontal(true);
		editComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayoutFactory.fillDefaults().generateLayout(parent);
		attachSelectionListener();
		activateContext();
		focusLatestWord();
	}

	@Override
	public void setFocus() {
	}

	@Override
	public void doSaveAs() {
	}

	@Override
	public boolean isDirty() {
		return dirtyable;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	@Override
	public boolean isSaveOnCloseNeeded() {
		return isDirty();
	}

	private void activateContext() {
		IContextService contextService = (IContextService) getSite()
				.getService(IContextService.class);
		contextService.activateContext(ID);
	}

	/* NOT WORKING */
	// @SuppressWarnings( "unused" )
	// private void addKeyBinding(Composite parent) {
	// Display display = parent.getDisplay();
	// display.setData(RWT.ACTIVE_KEYS, new String[] { "CTRL+S" });
	// display.addFilter(SWT.KeyDown, new Listener() {
	// @Override
	// public void handleEvent(Event event) {
	// if (event.stateMask == SWT.CTRL && event.character == 'S')
	// saveToXml((Page) event.data);
	// }
	// });
	//
	// }

	protected void setDirty(boolean dirty) {
		if (dirtyable != dirty) {
			dirtyable = dirty;
			firePropertyChange(IWorkbenchPartConstants.PROP_DIRTY);
		}
	}

	private void attachSelectionListener() {
		ISelectionService selectionService = (ISelectionService) getSite()
				.getService(ISelectionService.class);
		selectionService.addSelectionListener(new ISelectionListener() {
			@SuppressWarnings("unchecked")
			public void selectionChanged(IWorkbenchPart part,
					ISelection selection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				if (structuredSelection.getFirstElement() instanceof Page) {
					List<Page> pages = (List<Page>) structuredSelection
							.toList();
					if (pages != null && pages.size() > 0) {
						Page page = pages.get(0);
						if (dirtyable) {
							MessageDialog dialog = new MessageDialog(
									editComposite.getShell(),
									Messages.get().SavePage,
									null,
									Messages.get().CurrentPageModified,
									MessageDialog.CONFIRM,
									new String[] {
											IDialogConstantsHelper
													.getYesLabel(),
											IDialogConstantsHelper.getNoLabel() },
									0);
							// IDialogConstants.get().YES_LABEL,
							// IDialogConstants.get().NO_LABEL },
							// 0);
							dialog.create();
							if (dialog.open() == Window.OK) {
								doSave(null);
							}
						}
						editComposite.update(page);
						sc.setMinHeight(editComposite.computeSize(SWT.DEFAULT,
								SWT.DEFAULT).y);
						if (page.id().equals(
								DrcUiActivator.getDefault().currentUser()
										.latestPage())) {
							focusLatestWord();
						}
					}
				}
			}
		});

	}

	// /**
	// * Pass this view's context to the embedded composite
	// */
	// @PostConstruct
	// public void setContext() {
	// editComposite.context = context;
	// eventBroker = (IEventBroker) context.get(IEventBroker.class.getName());
	// }

	private void focusLatestWord() {
		if (editComposite != null && editComposite.getWords() != null) {
			Text text = editComposite.getWords().get(
					DrcUiActivator.getDefault().currentUser().latestWord());
			// text.setFocus(); // FIXME collides with page selection sometimes
			sc.showControl(text);
		}
	}

	// /**
	// * @param parent
	// * The parent composite for this part
	// * @param dirtyable
	// * The dirtyable to display edit status
	// */
	// @Inject
	// public EditView(final Composite parent, final MDirtyable dirtyable) {
	// }

	// /**
	// * @param pages
	// * The selected pages
	// */
	// @Inject
	// public void setSelection(
	// @Optional @Named(IServiceConstants.ACTIVE_SELECTION) final List<Page>
	// pages) {
	// if (pages != null && pages.size() > 0) {
	// Page page = pages.get(0);
	// if (dirtyable.isDirty()) {
	// MessageDialog dialog = new MessageDialog(
	// editComposite.getShell(), Messages.SavePage, null,
	// Messages.CurrentPageModified, MessageDialog.CONFIRM,
	// new String[] { IDialogConstants.get().YES_LABEL,
	// IDialogConstants.get().NO_LABEL }, 0);
	// dialog.create();
	// if (dialog.open() == Window.OK) {
	// doSave(null);
	// }
	// }
	// editComposite.update(page);
	// sc.setMinSize(editComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	// } else {
	// return;
	// }
	// dirtyable.setDirty(false);
	// }

	/**
	 * @param progressMonitor
	 *            To show progress during saving
	 */
	@Override
	public void doSave(/* @Optional */final IProgressMonitor progressMonitor) {
		final IProgressMonitor monitor = progressMonitor == null ? new NullProgressMonitor()
				: progressMonitor;
		final Page page = editComposite.getPage();
		monitor.beginTask(Messages.get().SavingPage, page.words().size());
		final List<Text> words = editComposite.getWords();
		editComposite.getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < words.size(); i++) {
					Text text = words.get(i);
					resetWarningColor(text);
					addToHistory(text);
					monitor.worked(1);
				}
				saveToXml(page);
				plainTextCopy(page);
				SearchView sv = (SearchView) PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage()
						.findView(SearchView.ID);
				sv.updateTreeViewer();
				WordView wv = (WordView) PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage()
						.findView(WordView.ID);
				Text text = editComposite.getPrev();
				Word word = (Word) text.getData(Word.class.toString());
				wv.selectedWord(word, text);
				// eventBroker.post(EditView.SAVED, page);
			}

			private void plainTextCopy(final Page page) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						String col = Index.DefaultCollection()
								+ PlainTextCopy.suffix();
						String vol = page.id().split("-")[0]; //$NON-NLS-1$
						XmlDb db = DrcUiActivator.getDefault().db();
						System.out.printf("Copy text to '%s', '%s' in %s\n", //$NON-NLS-1$
								col, vol, db);
						PlainTextCopy.saveToDb(page, col, vol, db);
					}
				}).start();
			}

			private void addToHistory(Text text) {
				String newText = text.getText();
				Word word = (Word) text.getData(Word.class.toString());
				Stack<Modification> history = word.history();
				Modification oldMod = history.top();
				if (!newText.equals(oldMod.form())
						&& !word.original().trim()
								.equals(Page.ParagraphMarker())) {
					User user = DrcUiActivator.getDefault().currentUser();
					if (!oldMod.author().equals(user.id())
							&& !oldMod.voters().contains(user.id())) {
						oldMod.downvote(user.id());
						User.withId(Index.DefaultCollection(),
								DrcUiActivator.getDefault().userDb(),
								oldMod.author()).wasDownvoted();
					}
					history.push(new Modification(newText, user.id()));
					user.hasEdited();
					user.save(DrcUiActivator.getDefault().userDb());
					text.setFocus();
				}
			}

			private void resetWarningColor(Text text) {
				if (text.getForeground()
						.equals(text.getDisplay().getSystemColor(
								EditComposite.DUBIOUS))) {
					text.setForeground(text.getDisplay().getSystemColor(
							EditComposite.DEFAULT));
					label.setText(""); //$NON-NLS-1$
				}
			}
		});
		// dirtyable.setDirty(false);
		setDirty(false);
	}

	private void saveToXml(final Page page) {
		System.out.println("Saving page: " + page); //$NON-NLS-1$
		page.saveToDb(DrcUiActivator.getDefault().currentUser().collection(),
				DrcUiActivator.getDefault().db());
	}

}
