/**
 * Standalone app entry for local development.
 * Wraps WeeklyCommitRoutes with a BrowserRouter and the mock host bridge.
 */
import { BrowserRouter } from "react-router-dom";
import WeeklyCommitRoutes from "./Routes.js";
import { mockHostBridge } from "./host/MockHostProvider.js";

export default function App() {
  return (
    <BrowserRouter basename="/weekly">
      <WeeklyCommitRoutes bridge={mockHostBridge} />
    </BrowserRouter>
  );
}
