import { test, expect } from "@playwright/test";
async function login(page, role = "admin") {
  await page.goto("/");
  await page.getByLabel("Work email").fill(`${role}@courier.local`);
  await page
    .getByLabel("Password", { exact: true })
    .fill(role === "admin" ? "DemoAdmin!2026" : "DemoAgent!2026");
  await page.getByRole("button", { name: "Sign in to workspace" }).click();
  await expect(
    page.getByRole("heading", { name: "Your delivery overview." }),
  ).toBeVisible();
}
test("admin registers, assigns and delivers a parcel; public tracking reflects history", async ({
  page,
}) => {
  await login(page);
  await page.getByRole("button", { name: "Register parcel" }).click();
  const dialog = page.getByRole("dialog");
  const data = {
    senderName: "E2E Sender",
    senderEmail: "sender@example.com",
    senderPhone: "+91 9000000000",
    senderAddress: "1 Sender Street",
    senderCity: "Bengaluru",
    recipientName: "E2E Recipient",
    recipientEmail: "recipient@example.com",
    recipientPhone: "+91 9111111111",
    recipientAddress: "2 Receiver Road",
    recipientCity: "Mumbai",
    description: "Test books",
    weightKg: "2.50",
    expectedDelivery: "2030-01-15",
  };
  for (const [key, value] of Object.entries(data))
    await dialog.locator(`[name="${key}"]`).fill(value);
  await dialog.getByRole("button", { name: "Register parcel" }).click();
  await expect(dialog.getByRole("heading", { level: 2 })).toHaveText(
    /^CT-[A-F0-9]{32}$/,
  );
  const id = await dialog.getByRole("heading", { level: 2 }).textContent();
  await dialog
    .getByLabel("Delivery agent", { exact: true })
    .selectOption({ label: "Sam Rivera" });
  await dialog
    .getByRole("button", { name: "Assign agent", exact: true })
    .click();
  await expect(
    dialog.getByText("Assigned to Sam Rivera.", { exact: true }),
  ).toBeVisible();
  for (const status of [
    "PICKED_UP",
    "IN_TRANSIT",
    "OUT_FOR_DELIVERY",
    "DELIVERED",
  ]) {
    await dialog.getByLabel("Next status").selectOption(status);
    await dialog.getByLabel("Current city or hub").fill("Mumbai");
    await dialog.getByLabel("Delivery note").fill(`E2E ${status}`);
    await dialog.getByRole("button", { name: "Save status update" }).click();
    await expect(
      dialog.getByText(`E2E ${status}`, { exact: true }),
    ).toBeVisible();
  }
  await expect(
    dialog.getByRole("button", { name: "Save status update" }),
  ).toHaveCount(0);
  await dialog.getByRole("button", { name: "Close dialog" }).click();
  await page
    .getByRole("button", { name: "All shipments", exact: true })
    .click();
  await page.getByRole("textbox", { name: "Search shipments" }).fill(id);
  await expect(
    page.getByRole("button", { name: id, exact: true }),
  ).toBeVisible();
  await page.getByLabel("Filter by status").selectOption("DELIVERED");
  await expect(
    page.getByRole("button", { name: id, exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Sign out" }).click();
  await page.goto(`/?id=${id}#track`);
  await expect(page.getByRole("heading", { name: id })).toBeVisible();
  await expect(page.getByText("Delivered", { exact: true })).toHaveCount(2);
  await expect(page.getByText("E2E Sender")).toHaveCount(0);
  await expect(page.getByText("recipient@example.com")).toHaveCount(0);
});
test("agent sees only assignments and can read an assignment notification", async ({
  page,
}) => {
  await login(page, "agent");
  await expect(
    page.getByRole("button", { name: "Register parcel" }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "My deliveries", exact: true })
    .click();
  await expect(page.getByText("CT-DEMO-1002", { exact: true })).toBeVisible();
  await expect(page.getByText("CT-DEMO-1001", { exact: true })).toHaveCount(0);
  await page
    .getByRole("button", { name: "Notifications", exact: false })
    .first()
    .click();
  await expect(page.getByText("New delivery assignment").first()).toBeVisible();
  await page.getByRole("button", { name: "Mark as read" }).first().click();
  await expect(
    page.getByText("Email: In App Only", { exact: false }).first(),
  ).toBeVisible();
});
test("public tracking handles missing parcels", async ({ page }) => {
  await page.goto("/#track");
  await page.getByLabel("Tracking ID").fill("CT-MISSING");
  await page.getByRole("button", { name: "Track parcel", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("No parcel found");
});
for (const viewport of [
  { width: 1440, height: 1000 },
  { width: 390, height: 844 },
]) {
  test(`responsive dashboard and navigation ${viewport.width}px`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await login(page);
    await page.screenshot({
      path: `test-results/dashboard-${viewport.width}.png`,
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBeTruthy();
    if (viewport.width < 680)
      await page.getByRole("button", { name: "Open navigation" }).click();
    await page
      .getByRole("button", { name: "Track a parcel", exact: true })
      .click();
    await page.getByLabel("Tracking ID").fill("CT-DEMO-1004");
    await page
      .getByRole("button", { name: "Track parcel", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "CT-DEMO-1004" }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBeTruthy();
    await page.screenshot({
      path: `test-results/tracking-${viewport.width}.png`,
      fullPage: true,
    });
  });
}
