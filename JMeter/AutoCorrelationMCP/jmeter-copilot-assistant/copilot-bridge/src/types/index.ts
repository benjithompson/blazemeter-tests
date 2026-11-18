export type CopilotRequest = {
  prompt: string;
  maxTokens?: number;
  temperature?: number;
  topP?: number;
  frequencyPenalty?: number;
  presencePenalty?: number;
};

export type CopilotResponse = {
  id: string;
  object: string;
  created: number;
  model: string;
  choices: Array<{
    text: string;
    index: number;
    logprobs?: any;
    finish_reason: string;
  }>;
};

export type TestResult = {
  testName: string;
  success: boolean;
  duration: number;
  errors?: string[];
};

export type TestPlan = {
  name: string;
  description: string;
  parameters: Record<string, any>;
};