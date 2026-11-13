"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.newVerificationToken = newVerificationToken;
exports.hashToken = hashToken;
const node_crypto_1 = require("node:crypto");
function newVerificationToken() {
    return (0, node_crypto_1.randomBytes)(32).toString('hex');
}
function hashToken(token) {
    return (0, node_crypto_1.createHash)('sha256').update(token).digest('hex');
}
//# sourceMappingURL=token.util.js.map