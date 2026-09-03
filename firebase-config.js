export const firebaseConfig = {
  apiKey: "PUT_API_KEY_HERE",
  authDomain: "PUT_PROJECT.firebaseapp.com",
  databaseURL: "https://PUT_PROJECT-default-rtdb.firebaseio.com",
  projectId: "PUT_PROJECT",
  storageBucket: "PUT_PROJECT.appspot.com",
  messagingSenderId: "PUT_SENDER_ID_HERE",
  appId: "PUT_APP_ID_HERE"
};

export const firebaseReady = !Object.values(firebaseConfig).some(v => String(v).includes("PUT_"));