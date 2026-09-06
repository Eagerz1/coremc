package net.coremc.store.api;

/** A persisted store purchase. */
public final class Purchase {

    public enum Type { KEY, BUNDLE, RANK }
    public enum Status { PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED }

    private final String id;
    private final java.util.UUID playerUuid;
    private final String playerName;
    private final String productId;
    private final Type type;
    private final String productName;
    private final double price;
    private final String transactionId;
    private final long createdAt;
    private Status status;

    public Purchase(final String id, final java.util.UUID playerUuid, final String playerName,
                    final String productId, final Type type, final String productName,
                    final double price, final String transactionId, final long createdAt, final Status status) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.productId = productId;
        this.type = type;
        this.productName = productName;
        this.price = price;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String id() { return id; }
    public java.util.UUID playerUuid() { return playerUuid; }
    public String playerName() { return playerName; }
    public String productId() { return productId; }
    public Type type() { return type; }
    public String productName() { return productName; }
    public double price() { return price; }
    public String transactionId() { return transactionId; }
    public long createdAt() { return createdAt; }
    public Status status() { return status; }
    public void status(final Status s) { this.status = s; }
}
