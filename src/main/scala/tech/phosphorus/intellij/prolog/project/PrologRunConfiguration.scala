package tech.phosphorus.intellij.prolog.project

import java.nio.file.Paths

import com.intellij.configurationStore.XmlSerializer
import com.intellij.execution.actions.PauseOutputAction
import com.intellij.execution.{DefaultExecutionResult, ExecutionResult, ExecutionTarget, Executor}
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, GeneralCommandLine, LocatableConfigurationBase, RunConfiguration, RunConfigurationBase, RunProfileState, RunnerSettings}
import com.intellij.execution.filters.{TextConsoleBuilder, TextConsoleBuilderFactory}
import com.intellij.execution.process.{ColoredProcessHandler, OSProcessHandler, ProcessTerminatedListener}
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.psi.search.GlobalSearchScope
import javax.swing.{Icon, JComponent}
import org.jdom.Element
import tech.phosphorus.intellij.prolog.psi.PrologFileType
import tech.phosphorus.intellij.prolog.settings.PrologRunConfigurationSettings
import tech.phosphorus.intellij.prolog.toolchain.PrologToolchain

class PrologRunConfigurationSettingsEditor(configuration: PrologRunConfiguration, project: Project) extends PrologRunConfigurationSettings {
  {
    targetRunFile.addBrowseFolderListener("Select file", "Select target run file", project, FileChooserDescriptorFactory.createSingleFileDescriptor(PrologFileType.INSTANCE))
  }

  override def resetEditorFrom(settings: PrologRunConfiguration): Unit = {
    targetRunFile.setText(settings.targetFile)
  }

  override def applyEditorTo(settings: PrologRunConfiguration): Unit = {
    settings.targetFile = targetRunFile.getText
  }

  override def createEditor(): JComponent = rootPanel
}

class PrologRunConfiguration(val project: Project, factory: ConfigurationFactory) extends LocatableConfigurationBase[PrologRunProfileState](project, factory) {
  var targetFile = ""

  lazy val toolchain = new PrologToolchain(Paths.get(PrologToolchain.instanceToolchain()))

  @SuppressWarnings(Array("deprecated"))
  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "targetFile", targetFile)
    PathMacroManager.getInstance(project).collapsePathsRecursively(element)
  }

  @SuppressWarnings(Array("deprecated"))
  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    PathMacroManager.getInstance(project).expandPaths(element)
    val file = JDOMExternalizer.readString(element, "targetFile")
    if (file != null) targetFile = file
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new PrologRunConfigurationSettingsEditor(this, project)

  override def getState(executor: Executor, executionEnvironment: ExecutionEnvironment): PrologRunProfileState = new PrologRunProfileState(this, executionEnvironment)
}

class PrologRunConfigurationFactory(ty: PrologRunConfigurationType) extends ConfigurationFactory(ty) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = new PrologRunConfiguration(project, this)
}

class PrologRunConfigurationType extends ConfigurationType {
  val factories: Array[ConfigurationFactory] = Array(new PrologRunConfigurationFactory(this))

  override def getDisplayName: String = "Prolog"

  override def getConfigurationTypeDescription: String = "Prolog bootstrap run config"

  override def getIcon: Icon = null

  override def getId: String = "PrologRunConfiguration"

  override def getConfigurationFactories: Array[ConfigurationFactory] = factories
}

class PrologRunProfileState(configuration: PrologRunConfiguration, executionEnvironment: ExecutionEnvironment) extends RunProfileState {

  val consoleBuilder: TextConsoleBuilder = TextConsoleBuilderFactory
    .getInstance()
    .createBuilder(configuration.project, GlobalSearchScope.allScope(configuration.project))

  override def execute(executor: Executor, programRunner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
    val handler = new ColoredProcessHandler(new GeneralCommandLine(configuration.toolchain.executablePath.toString).withParameters("-t", "halt", "-q", configuration.targetFile))
    ProcessTerminatedListener.attach(handler)
    val console = consoleBuilder.getConsole
    console.attachToProcess(handler)
    handler.startNotify()
    new DefaultExecutionResult(console, handler)
  }
}
