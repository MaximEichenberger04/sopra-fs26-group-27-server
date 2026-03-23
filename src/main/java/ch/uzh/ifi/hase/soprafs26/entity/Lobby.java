package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.lang.annotation.Inherited;

@Entity
@Table(name = "lobbies")
public class Lobby implements Serializable {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name; // lobby name

    @Column(nullable = false, unique = true)
    private String inviteCode; 

    @Column(nullable = false)
    private Long hostId; // user id from host

    @Column(nullable = false)
    private int playerCount;

    @Column(nullable = false)
    private int currentPlayers;

    @Column(nullable = false)
    private String gameMode;

    @Column(nullable = false)
    private String gameStatus; 

    // map layout, board size, game mode, theme etc missing right now.
}
