import { useState } from 'react';
import { commandService } from '../services/commandService';
import { useAuth } from '../contexts/AuthContext';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export function Chat() {
  const { isAuthenticated, setShowLoginRequired } = useAuth();
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;

    if (!isAuthenticated) {
      setShowLoginRequired(true);
      return;
    }

    const userMessage: ChatMessage = { role: 'user', content: input };
    setMessages(prev => [...prev, userMessage]);
    setLoading(true);
    setInput('');

    try {
      const nlRes = await commandService.mcpQuery({ query: input });
      const response = nlRes.data;
      
      const assistantMessage: ChatMessage = {
        role: 'assistant',
        content: response.message || 'Done',
      };
      setMessages(prev => [...prev, assistantMessage]);
    } catch (error: any) {
      console.error('Error:', error);
      if (error.response?.status === 401) {
        setShowLoginRequired(true);
      }
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: 'Sorry, I could not process your request.',
      }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-180px)]">
      <div className="mb-4">
        <h1 className="text-2xl font-bold text-gray-900">Jira Ops Assistant</h1>
        <p className="text-gray-600">Describe what you want to do in natural language</p>
      </div>

      <div className="flex-1 bg-white rounded-lg shadow-md border border-gray-200 overflow-hidden flex flex-col">
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.length === 0 && (
            <div className="text-center text-gray-500 py-8">
              <p>Start typing to interact with Jira using natural language</p>
              <p className="text-sm mt-2">Example: "fetch my issues"</p>
            </div>
          )}
          {messages.map((msg, idx) => (
            <div key={idx} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[80%] rounded-lg p-3 ${
                msg.role === 'user' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-900'
              }`}>
                <p className="whitespace-pre-wrap">{msg.content}</p>
              </div>
            </div>
          ))}
          {loading && (
            <div className="flex justify-start">
              <div className="bg-gray-100 rounded-lg p-3">
                <div className="flex space-x-2">
                  <div className="w-2 h-2 bg-gray-500 rounded-full animate-bounce"></div>
                  <div className="w-2 h-2 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }}></div>
                  <div className="w-2 h-2 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                </div>
              </div>
            </div>
          )}
        </div>

        <form onSubmit={handleSubmit} className="border-t border-gray-200 p-4">
          <div className="flex gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={isAuthenticated ? "Describe what you want to do..." : "Please login to chat with Jira Ops"}
              className="flex-1 border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              disabled={loading}
            />
            <button
              type="submit"
              disabled={loading || !input.trim()}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              Send
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
