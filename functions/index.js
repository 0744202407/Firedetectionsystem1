const {onDocumentUpdated} = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onFireEmergencyPush = onDocumentUpdated(
    "fire_status/current",
    async (event) => {
      const newValue = event.data.after.data();
      const previousValue = event.data.before.data();

      if (!newValue) {
        return null;
      }

      const status = newValue.status;
      const updateTick = newValue.updateTick;
      const oldUpdateTick = previousValue ?
        previousValue.updateTick : null;

      if (
        (status === "FIRE" || status === "WARNING") &&
        updateTick !== oldUpdateTick
      ) {
        const payload = {
          notification: {
            title: status === "FIRE" ?
              "!!! EMERGENCY: FIRE DETECTED !!!" :
              "WARNING: HIGH SMOKE",
            body:
              "Danger detected at Station 01. Open the app immediately.",
          },
          data: {
            status: status,
            click_action: "FLUTTER_NOTIFICATION_CLICK",
          },
          topic: "fire_alerts",
        };

        const options = {
          android: {
            priority: "high",
            notification: {
              sound: "default",
              clickAction: "FLUTTER_NOTIFICATION_CLICK",
              channelId: "FireCriticalAlerts",
            },
          },
        };

        try {
          await admin.messaging().send({
            ...payload,
            ...options,
          });

          console.log(
              `FCM Alert Sent! Status: ${status}, Tick: ${updateTick}`,
          );
        } catch (error) {
          console.error(
              "Error sending FCM notification:",
              error,
          );
        }
      } else {
        console.log(
            "Status is SAFE or no new tick. Notification not sent.",
        );
      }

      return null;
    });
