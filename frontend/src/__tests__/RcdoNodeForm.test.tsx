import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { RcdoNodeForm } from "../components/rcdo/RcdoNodeForm.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";

// ── Test fixture ──────────────────────────────────────────────────────────────

const mockTree: RcdoTreeNode[] = [
  {
    id: "rc-1",
    nodeType: "RALLY_CRY",
    status: "ACTIVE",
    title: "Grow Revenue",
    children: [
      {
        id: "do-1",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Enterprise Sales",
        children: [],
      },
    ],
  },
];

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("RcdoNodeForm — create mode", () => {
  it("renders the form with the correct heading for RALLY_CRY", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByRole("form", { name: "Create Rally Cry" })).toBeInTheDocument();
  });

  it("requires title — shows error when submitted empty", async () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Title is required");
    });
  });

  it("does not call onSubmit when title is empty", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("clears title error after user types a value", async () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    // Trigger error
    fireEvent.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    // Type in the title field
    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "A new RC" },
    });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("does NOT show parent selector for RALLY_CRY", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(
      screen.queryByLabelText(/Parent/),
    ).not.toBeInTheDocument();
  });

  it("shows parent Rally Cry selector for DEFINING_OBJECTIVE", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="DEFINING_OBJECTIVE"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(
      screen.getByLabelText(/Parent Rally Cry/),
    ).toBeInTheDocument();
  });

  it("requires parent when creating a DEFINING_OBJECTIVE", async () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="DEFINING_OBJECTIVE"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "New DO" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "A Rally Cry parent is required",
      );
    });
  });

  it("shows parent Defining Objective selector for OUTCOME", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="OUTCOME"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(
      screen.getByLabelText(/Parent Defining Objective/),
    ).toBeInTheDocument();
  });

  it("populates Rally Cry options in the parent selector for DO", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="DEFINING_OBJECTIVE"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    const selector = screen.getByLabelText(/Parent Rally Cry/);
    expect(selector).toHaveTextContent("Grow Revenue");
  });

  it("pre-selects defaultParentId in the parent selector", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="DEFINING_OBJECTIVE"
        defaultParentId="rc-1"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    const selector = screen.getByLabelText(/Parent Rally Cry/) as HTMLSelectElement;
    expect(selector.value).toBe("rc-1");
  });

  it("does NOT show target metric field for RALLY_CRY", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText("Target Metric")).not.toBeInTheDocument();
  });

  it("does NOT show target metric field for DEFINING_OBJECTIVE", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="DEFINING_OBJECTIVE"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText("Target Metric")).not.toBeInTheDocument();
  });

  it("shows target metric field for OUTCOME only", () => {
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="OUTCOME"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByLabelText("Target Metric")).toBeInTheDocument();
  });

  it("stores target metric in the description payload for outcomes", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="OUTCOME"
        tree={mockTree}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "Close 10 Deals" },
    });
    fireEvent.change(screen.getByLabelText(/Parent Defining Objective/), {
      target: { value: "do-1" },
    });
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: "Core sales outcome" },
    });
    fireEvent.change(screen.getByLabelText("Target Metric"), {
      target: { value: "10 signed deals" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        description: "Core sales outcome\n\nTarget metric: 10 signed deals",
      }),
    );
  });

  it("calls onSubmit with correct payload when form is valid", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "Drive Growth" },
    });
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: "Scale up revenue" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        nodeType: "RALLY_CRY",
        title: "Drive Growth",
        description: "Scale up revenue",
      }),
    );
  });

  it("calls onCancel when Cancel is clicked", () => {
    const onCancel = vi.fn();
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={onCancel}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("shows API error when onSubmit rejects", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("Network failure"));
    render(
      <RcdoNodeForm
        mode="create"
        nodeType="RALLY_CRY"
        tree={mockTree}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "Some title" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Network failure");
    });
  });
});

describe("RcdoNodeForm — edit mode", () => {
  it("renders the form with the correct heading for OUTCOME", () => {
    render(
      <RcdoNodeForm
        mode="edit"
        nodeType="OUTCOME"
        status="ACTIVE"
        initialValues={{ title: "Close 10 Deals" }}
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByRole("form", { name: "Edit Outcome" })).toBeInTheDocument();
  });

  it("pre-fills title with initialValues", () => {
    render(
      <RcdoNodeForm
        mode="edit"
        nodeType="OUTCOME"
        status="ACTIVE"
        initialValues={{ title: "Close 10 Deals" }}
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByLabelText(/Title/)).toHaveValue("Close 10 Deals");
  });

  it("shows the current status as read-only text", () => {
    render(
      <RcdoNodeForm
        mode="edit"
        nodeType="RALLY_CRY"
        status="DRAFT"
        initialValues={{ title: "My RC" }}
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByText("DRAFT")).toBeInTheDocument();
  });

  it("does NOT show parent selector in edit mode", () => {
    render(
      <RcdoNodeForm
        mode="edit"
        nodeType="DEFINING_OBJECTIVE"
        status="ACTIVE"
        initialValues={{ title: "Enterprise Sales" }}
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText(/Parent/)).not.toBeInTheDocument();
  });

  it("submit button shows Save changes label in edit mode", () => {
    render(
      <RcdoNodeForm
        mode="edit"
        nodeType="RALLY_CRY"
        status="ACTIVE"
        initialValues={{ title: "My RC" }}
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: "Save changes" })).toBeInTheDocument();
  });

  it("still validates title in edit mode", async () => {
    render(
      <RcdoNodeForm
        mode="edit"
        nodeType="RALLY_CRY"
        status="ACTIVE"
        initialValues={{ title: "My RC" }}
        tree={mockTree}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Title is required");
    });
  });
});
