package tech.alius.rc.ui

import tech.alius.rc.device.TeleautoDeviceType

/** Exhaustive type identity shared by cards, covers and summaries. */
internal fun deviceIconResource(type: TeleautoDeviceType): Int = when (type) {
    TeleautoDeviceType.REMOTE_CONTROLLER -> R.drawable.ic_device_remote_controller
    TeleautoDeviceType.GENERIC_AIRCRAFT -> R.drawable.ic_device_generic_aircraft
    TeleautoDeviceType.FIXED_WING -> R.drawable.ic_device_fixed_wing
    TeleautoDeviceType.QUADROTOR -> R.drawable.ic_device_quadrotor
    TeleautoDeviceType.COAXIAL_HELICOPTER -> R.drawable.ic_device_coaxial_helicopter
    TeleautoDeviceType.HELICOPTER -> R.drawable.ic_device_helicopter
    TeleautoDeviceType.AIRSHIP -> R.drawable.ic_device_airship
    TeleautoDeviceType.FREE_BALLOON -> R.drawable.ic_device_free_balloon
    TeleautoDeviceType.ROCKET -> R.drawable.ic_device_rocket
    TeleautoDeviceType.GROUND_ROVER -> R.drawable.ic_device_ground_rover
    TeleautoDeviceType.SURFACE_BOAT -> R.drawable.ic_device_surface_boat
    TeleautoDeviceType.SUBMARINE -> R.drawable.ic_device_submarine
    TeleautoDeviceType.HEXAROTOR -> R.drawable.ic_device_hexarotor
    TeleautoDeviceType.OCTOROTOR -> R.drawable.ic_device_octorotor
    TeleautoDeviceType.TRICOPTER -> R.drawable.ic_device_tricopter
    TeleautoDeviceType.FLAPPING_WING -> R.drawable.ic_device_flapping_wing
    TeleautoDeviceType.KITE -> R.drawable.ic_device_kite
    TeleautoDeviceType.VTOL_TAILSITTER_DUOROTOR -> R.drawable.ic_device_vtol_tailsitter_duorotor
    TeleautoDeviceType.VTOL_TAILSITTER_QUADROTOR -> R.drawable.ic_device_vtol_tailsitter_quadrotor
    TeleautoDeviceType.VTOL_TILTROTOR -> R.drawable.ic_device_vtol_tiltrotor
    TeleautoDeviceType.VTOL_FIXEDROTOR -> R.drawable.ic_device_vtol_fixedrotor
    TeleautoDeviceType.VTOL_TAILSITTER -> R.drawable.ic_device_vtol_tailsitter
    TeleautoDeviceType.VTOL_TILTWING -> R.drawable.ic_device_vtol_tiltwing
    TeleautoDeviceType.PARAFOIL -> R.drawable.ic_device_parafoil
    TeleautoDeviceType.DODECAROTOR -> R.drawable.ic_device_dodecarotor
    TeleautoDeviceType.DECAROTOR -> R.drawable.ic_device_decarotor
    TeleautoDeviceType.GENERIC_MULTIROTOR -> R.drawable.ic_device_generic_multirotor
    TeleautoDeviceType.GROUND_QUADRUPED -> R.drawable.ic_device_quadruped
    TeleautoDeviceType.HUMANOID_ROBOT -> R.drawable.ic_device_humanoid
    TeleautoDeviceType.VTOL_GYRODYNE -> R.drawable.ic_device_vtol_gyrodyne
    TeleautoDeviceType.CYBERBRICK -> R.drawable.ic_device_cyberbrick
    TeleautoDeviceType.VIRTUAL_GAMEPAD -> R.drawable.ic_device_gamepad
    TeleautoDeviceType.VIRTUAL_JOYSTICK -> R.drawable.ic_device_joystick
}
