import express from "express";
import mongoose from "mongoose";

const app = express();
app.use(express.json());

await mongoose.connect("mongodb://127.0.0.1:27017/bankApp");


const userSchema = new mongoose.Schema({
  name: String,
  email: { type: String, unique: true },
  balance: { type: Number, default: 0 }
});

// Transaction Schema
const transactionSchema = new mongoose.Schema({
  sender: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
  receiver: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
  amount: Number,
  createdAt: { type: Date, default: Date.now }
});

const User = mongoose.model("User", userSchema);
const Transaction = mongoose.model("Transaction", transactionSchema);

// Register User
app.post("/register", async (req, res) => {
  try {
    const { name, email, balance } = req.body;
    const user = new User({ name, email, balance });
    await user.save();
    res.json({ success: true, user });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// Transfer Money
app.post("/transfer", async (req, res) => {
  const session = await mongoose.startSession();
  session.startTransaction();

  try {
    const { senderEmail, receiverEmail, amount } = req.body;

    const sender = await User.findOne({ email: senderEmail }).session(session);
    const receiver = await User.findOne({ email: receiverEmail }).session(session);

    if (!sender || !receiver) throw new Error("Invalid users");
    if (sender.balance < amount) throw new Error("Insufficient balance");

    sender.balance -= amount;
    receiver.balance += amount;

    await sender.save({ session });
    await receiver.save({ session });

    const transaction = new Transaction({ sender: sender._id, receiver: receiver._id, amount });
    await transaction.save({ session });

    await session.commitTransaction();
    session.endSession();

    res.json({ success: true, transaction });
  } catch (err) {
    await session.abortTransaction();
    session.endSession();
    res.status(400).json({ error: err.message });
  }
});

// Get Transactions
app.get("/transactions/:email", async (req, res) => {
  try {
    const user = await User.findOne({ email: req.params.email });
    if (!user) throw new Error("User not found");

    const transactions = await Transaction.find({
      $or: [{ sender: user._id }, { receiver: user._id }]
    })
      .populate("sender", "name email")
      .populate("receiver", "name email");

    res.json({ success: true, transactions });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.listen(3000, () => console.log("Server running on port 3000"));
