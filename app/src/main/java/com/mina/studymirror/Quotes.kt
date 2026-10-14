package com.mina.studymirror

object Quotes {
    private val all = listOf(
        "We suffer more often in imagination than in reality." to "Seneca",
        "It is not that we have a short time to live, but that we waste much of it." to "Seneca",
        "What stands in the way becomes the way." to "Marcus Aurelius",
        "Waste no more time arguing about what a good man should be. Be one." to "Marcus Aurelius",
        "The soul becomes dyed with the colour of its thoughts." to "Marcus Aurelius",
        "He who has a why to live can bear almost any how." to "Friedrich Nietzsche",
        "The unexamined life is not worth living." to "Socrates",
        "We are what we repeatedly do. Excellence, then, is not an act, but a habit." to "Will Durant",
        "A journey of a thousand miles begins with a single step." to "Lao Tzu",
        "Courage is not the absence of fear, but the triumph over it." to "Nelson Mandela",
        "You must do the thing you think you cannot do." to "Eleanor Roosevelt",
        "Success is the sum of small efforts, repeated day in and day out." to "Robert Collier",
        "The only way to do great work is to love what you do." to "Steve Jobs",
        "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
        "The best way out is always through." to "Robert Frost",
        "Fall seven times, stand up eight." to "Japanese proverb",
        "The good physician treats the disease; the great physician treats the patient who has the disease." to "William Osler",
        "Medicine is a science of uncertainty and an art of probability." to "William Osler",
        "Wherever the art of Medicine is loved, there is also a love of Humanity." to "Hippocrates"
    )

    fun pick(seed: Int): Pair<String, String> = all[Math.floorMod(seed, all.size)]
    fun random(): Pair<String, String> = all.random()
    fun line(): String = random().let { "\u201C${it.first}\u201D\n\u2014 ${it.second}" }
}
