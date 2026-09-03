export const firebaseConfig = {
  apiKey: "AIzaSyD121IwvJ1RXq-WsYof6mDCwJxco1IBDy8",
  authDomain: "delivery-tracker-febcc.firebaseapp.com",
  databaseURL: "https://delivery-tracker-febcc-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "delivery-tracker-febcc",
  storageBucket: "delivery-tracker-febcc.firebasestorage.app",
  messagingSenderId: "1038366061508",
  appId: "1:1038366061508:web:7d34a4ada107c3a13b2a91"
};

export const firebaseReady = !Object.values(firebaseConfig).some(v => String(v).includes("PUT_"));