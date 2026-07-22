const {onDocumentUpdated} = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

exports.onFireAlertTrigger = onDocumentUpdated("fire_status/current", async (event) => {
    const data = event.data.after.data();
    const prev = event.data.before.data();
    if (!data) return;

    const status = data.status;
    const tick = data.updateTick;
    const oldTick = prev ? prev.updateTick : null;

    // Send FCM if status is DANGER and updateTick has changed
    if ((status === "FIRE" || status === "WARNING") && tick !== oldTick) {
        const payload = {
            notification: {
                title: status === "FIRE" ? "!!! EMERGENCY: FIRE !!!" : "WARNING: SMOKE",
                body: "Danger detected! Please check the app immediately.",
            },
            topic: "fire_alerts"
        };
        await admin.messaging().send(payload);
        console.log("FCM Notification Sent for:", status);
    }
});
