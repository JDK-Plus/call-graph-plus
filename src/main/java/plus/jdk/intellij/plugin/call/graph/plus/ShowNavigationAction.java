package plus.jdk.intellij.plugin.call.graph.plus;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ShowNavigationAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(AnActionEvent anActionEvent) {
    Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
    PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);
    Project project = anActionEvent.getProject();
    if (editor == null || psiFile == null || project == null) {
      return;
    }
    int offset = editor.getCaretModel().getOffset();

    final StringBuilder infoBuilder = new StringBuilder();
    PsiElement element = psiFile.findElementAt(offset);
    infoBuilder.append("Element at caret: ").append(element).append("\n");
    if (element != null) {
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      infoBuilder
              .append("Containing method: ")
              .append(containingMethod != null ? containingMethod.getName() : "none")
              .append("\n");
      if (containingMethod != null) {
        PsiClass containingClass = containingMethod.getContainingClass();
        infoBuilder
                .append("Containing class: ")
                .append(containingClass != null ? containingClass.getName() : "none")
                .append("\n");

        infoBuilder.append("Local variables:\n");
        List<String> localVariables = new ArrayList<>();
        containingMethod.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            localVariables.add(variable.getName());
          }
        });
        for (String localVar : localVariables) {
          infoBuilder.append(localVar).append("\n");
        }

        infoBuilder.append("Method calls:\n");
        List<String> methodCalls = new ArrayList<>();
        containingMethod.accept(new JavaRecursiveElementVisitor() {
          @Override
          public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            PsiElement resolve = methodExpression.resolve();
            if (resolve instanceof PsiMethod) {
              PsiMethod calledMethod = (PsiMethod) resolve;
              methodCalls.add(calledMethod.getName());
            }
          }
        });
        for (String methodCall : methodCalls) {
          infoBuilder.append(methodCall).append("\n");
        }
      }
    }

    // Show information in ToolWindow
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("call-graph-window");
    if (toolWindow != null) {
      toolWindow.activate(() -> {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        JTextArea textArea = new JTextArea(infoBuilder.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        toolWindow.getContentManager().removeAllContents(true);
        toolWindow.getContentManager().addContent(toolWindow.getContentManager().getFactory().createContent(contentPanel, "", false));
      });
    } else {
      Messages.showMessageDialog(project, "ToolWindow not found", "Error", Messages.getErrorIcon());
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    e.getPresentation().setEnabled(editor != null && psiFile != null);
  }
}
