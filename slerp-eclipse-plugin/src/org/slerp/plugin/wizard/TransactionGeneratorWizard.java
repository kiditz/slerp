package org.slerp.plugin.wizard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.slerp.core.Dto;
import org.slerp.generator.TransactionGenerator;
import org.springframework.util.StringUtils;

public class TransactionGeneratorWizard extends Wizard implements INewWizard {
	private ISelection selection;
	private TransactionGeneratorPage page;

	/**
	 * Constructor for {@linkplain TransactionGeneratorWizard}.
	 */
	public TransactionGeneratorWizard() {
		super();
		setNeedsProgressMonitor(true);
	}

	/**
	 * Adding the page to the wizard.
	 */

	public void addPages() {
		page = new TransactionGeneratorPage(selection);
		addPage(page);
	}

	public boolean performFinish() {
		final String entityPackage = page.getTxtEntityPackage().getText();
		final String repositoryPackage = page.getTxtRepoPackage().getText();
		// final IResource propertiesFile = page.getApplicationProperties();
		IProject project = page.getProject().getProject();
		final String packageService = page.getTxtServicePackage().getText();
		final String transactionMode = page.getTransactionMode();
		final String[] items = page.getListReceiver().getItems();

		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					doFinish(monitor, entityPackage, repositoryPackage, packageService, transactionMode, items,
							project);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		};

		try {
			getContainer().run(true, false, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			e.printStackTrace();
			Throwable realException = e.getTargetException();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}
		return true;
	}

	private void doFinish(IProgressMonitor monitor, String entityPackage, String repositoryPackage,
			String servicePackage, String transactionMode, String[] items, IProject project) throws CoreException {
		if (StringUtils.isEmpty(transactionMode)) {
			throwCoreException("Please select transaction mode you wanted to be!");
		}
		String baseDir = project.getLocation().toOSString().concat("/src/main/java");
		System.err.println(baseDir);
		boolean enablePrepare = page.isEnablePrepare();
		TransactionGenerator generator = new TransactionGenerator(transactionMode, entityPackage, repositoryPackage,
				servicePackage, new File(baseDir), enablePrepare);
		if (items.length == 0) {
			throwCoreException("Please choose at lease one entity");
			return;
		}
		for (String item : items) {
			monitor.beginTask("Generate Business Transaction For  : " + item, 2);
			generator.generate(item);
		}

		Dto cacheDto = new Dto();
		cacheDto.put("packageEntity", entityPackage);
		cacheDto.put("packageRepo", repositoryPackage);
		cacheDto.put("packageService", servicePackage);
		cacheDto.put("enablePrepare", enablePrepare);
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		try {
			File cacheDir = project.findMember("src/main/resources").getLocation().toFile();
			File cacheOut = new File(cacheDir, "generator.cache");
			FileWriter writer = new FileWriter(cacheOut);
			writer.write(cacheDto.toString());
			writer.close();
		} catch (IOException e1) {
			throwCoreException("Failed to write file \n" + e1.getMessage());
		}
		monitor.worked(1);

		getShell().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				File baseDir = project.findMember("src/main/java").getLocation().toFile();
				File packageDir = new File(baseDir, servicePackage.replace(".", "/"));
				for (String item : items) {
					String filename = transactionMode + item + ".java";
					File file = new File(packageDir, filename);
					IFileStore fileStoreService = EFS.getLocalFileSystem().getStore(file.toURI());
					monitor.setTaskName("Opening file : " + file.getName());
					if (!fileStoreService.fetchInfo().isDirectory() && fileStoreService.fetchInfo().exists()) {
						try {
							IDE.openEditorOnFileStore(page, fileStoreService);
						} catch (PartInitException e) {
							e.printStackTrace();
						}
					}
				}

			}

		});

	}

	protected void throwCoreException(String message) throws CoreException {
		IStatus status = new Status(IStatus.ERROR, "slerp-eclipse-plugin", IStatus.OK, message, null);
		throw new CoreException(status);
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
	}

	public static void main(String[] args) {
		WizardDialog dialog = new WizardDialog(new Display().getActiveShell(), new TransactionGeneratorWizard());
		dialog.open();
	}
}