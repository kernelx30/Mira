import * as planModeI18n from "./plan_mode_i18n.js";

export type StartPlanImplementationResult = {
  success: boolean;
  error?: string;
};

export const PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL = "plan_mode.start_implementation";

export async function startPlanImplementation(
  planContent: string
): Promise<StartPlanImplementationResult> {
  const text = planModeI18n.resolvePlanModeI18n();
  const normalizedPlanContent = planContent.trim();
  if (!normalizedPlanContent) {
    const message = text.toastPlanEmpty;
    await Tools.System.toast(message);
    return { success: false, error: message };
  }

  try {
    return await ToolPkg.ipc.call<string, StartPlanImplementationResult>(
      PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL,
      normalizedPlanContent
    );
  } catch (error) {
    const errorText = error instanceof Error
      ? error.message || "error"
      : (typeof error === "string" || error == null ? error || "error" : "error");
    const message = `${text.toastPlanWriteFailedPrefix}${errorText}`;
    console.error(
      `[plan_mode_execution] startPlanImplementation failed: channel=${PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL}, planLength=${normalizedPlanContent.length}, error=${errorText}`
    );
    await Tools.System.toast(message);
    return { success: false, error: message };
  }
}
