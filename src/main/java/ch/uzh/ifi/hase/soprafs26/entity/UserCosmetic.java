package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Represents a single cosmetic item owned by a user.
 * Normalises what was previously a comma-separated string in the users table
 * into a proper one-to-many relationship (2NF).
 */
@Entity
@Table(name = "user_cosmetics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "cosmetic_id"}))
public class UserCosmetic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    /** The owning user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The cosmetic item identifier (e.g. "border-wood", "pawn-angel"). */
    @Column(name = "cosmetic_id", nullable = false)
    private String cosmeticId;

    public UserCosmetic() {
    }

    public UserCosmetic(User user, String cosmeticId) {
        this.user = user;
        this.cosmeticId = cosmeticId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCosmeticId() {
        return cosmeticId;
    }

    public void setCosmeticId(String cosmeticId) {
        this.cosmeticId = cosmeticId;
    }
}
