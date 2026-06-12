package com.wheezy.server.Models

import jakarta.persistence.*

@Entity
@Table(name = "locations")
data class LocationModel(
    @Id
    @Column(name = "location_id")
    val id: Long? = null,

    @Column(name = "city_name", nullable = false, unique = true)
    val name: String =  ""
)
