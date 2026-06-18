package ru.hopec.core

interface Service

class Services(val services: MutableList<Service> = mutableListOf()) {
    inline fun <reified T : Service> get() = services.filterIsInstance<T>().firstOrNull()

    fun <T: Service> addService(service: T) = services.add(service)
}
