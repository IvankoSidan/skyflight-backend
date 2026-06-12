package com.wheezy.server.Repository

import com.wheezy.server.Models.LocationModel
import org.springframework.data.jpa.repository.JpaRepository

interface LocationRepository : JpaRepository<LocationModel, Long>
