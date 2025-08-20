const express = require("express");
const mongoose = require("mongoose");
const jwt = require("jsonwebtoken");

const app = express();
app.use(express.json());

// MongoDB Setup
mongoose.connect("mongodb://localhost:27017/shop", {
  useNewUrlParser: true,
  useUnifiedTopology: true,
});

const Product = mongoose.model("Product", {
  name: String,
  price: Number,
});

// Middleware for JWT auth

function authenticateToken(req, res, next) {
  const token = req.headers["authorization"];
  if (!token) return res.sendStatus(401);

  jwt.verify(token, "secret_key", (err, user) => {
    if (err) return res.sendStatus(403);
    req.user = user;
    next();
  });
}

// Routes
app.get("/products", authenticateToken, async (req, res) => {
  const products = await Product.find();
  res.json(products);
});

app.post("/products", authenticateToken, async (req, res) => {
  const product = new Product(req.body);
  await product.save();
  res.status(201).json(product);
});

app.listen(3000, () => console.log("Server running on port 3000"));
