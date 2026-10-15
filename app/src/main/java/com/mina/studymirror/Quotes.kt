package com.mina.studymirror

/**
 * Words from people who thought hard about discipline, suffering, learning and rest.
 * Three moods: focus (during a block), rest (during a break), calm (when an urge hits).
 */
object Quotes {

    private val focus = listOf(
        "We suffer more often in imagination than in reality." to "Seneca",
        "It is not that we have a short time to live, but that we waste much of it." to "Seneca",
        "While we are postponing, life speeds by." to "Seneca",
        "Begin at once to live, and count each separate day as a separate life." to "Seneca",
        "It is not because things are difficult that we do not dare; it is because we do not dare that they are difficult." to "Seneca",
        "Difficulties strengthen the mind, as labor does the body." to "Seneca",
        "What stands in the way becomes the way." to "Marcus Aurelius",
        "You could leave life right now. Let that determine what you do and say and think." to "Marcus Aurelius",
        "Waste no more time arguing about what a good man should be. Be one." to "Marcus Aurelius",
        "The soul becomes dyed with the colour of its thoughts." to "Marcus Aurelius",
        "First say to yourself what you would be; and then do what you have to do." to "Epictetus",
        "No man is free who is not master of himself." to "Epictetus",
        "He who has a why to live can bear almost any how." to "Friedrich Nietzsche",
        "Everything can be taken from a man but one thing: the last of the human freedoms, to choose one's attitude in any given set of circumstances." to "Viktor Frankl",
        "When we are no longer able to change a situation, we are challenged to change ourselves." to "Viktor Frankl",
        "The unexamined life is not worth living." to "Socrates",
        "We are what we repeatedly do. Excellence, then, is not an act, but a habit." to "Will Durant",
        "You do not rise to the level of your goals. You fall to the level of your systems." to "James Clear",
        "Every action you take is a vote for the type of person you wish to become." to "James Clear",
        "The more important a call or action is to our soul's evolution, the more Resistance we will feel toward pursuing it." to "Steven Pressfield",
        "We must all suffer from one of two pains: the pain of discipline or the pain of regret." to "Jim Rohn",
        "Enthusiasm is common. Endurance is rare." to "Angela Duckworth",
        "Clarity about what matters provides clarity about what does not." to "Cal Newport",
        "The only way to do great work is to love what you do." to "Steve Jobs",
        "Your time is limited, so don't waste it living someone else's life." to "Steve Jobs",
        "Knowing is not enough; we must apply. Willing is not enough; we must do." to "Johann Wolfgang von Goethe",
        "Dripping water hollows out stone, not through force but through persistence." to "Ovid",
        "I fear not the man who has practiced 10,000 kicks once, but I fear the man who has practiced one kick 10,000 times." to "Bruce Lee",
        "I hated every minute of training, but I said, 'Don't quit. Suffer now and live the rest of your life as a champion.'" to "Muhammad Ali",
        "I've failed over and over and over again in my life. And that is why I succeed." to "Michael Jordan",
        "I can accept failure, everyone fails at something. But I can't accept not trying." to "Michael Jordan",
        "Everything negative, pressure, challenges, is all an opportunity for me to rise." to "Kobe Bryant",
        "Genius is one percent inspiration and ninety-nine percent perspiration." to "Thomas Edison",
        "Ever tried. Ever failed. No matter. Try again. Fail again. Fail better." to "Samuel Beckett",
        "Never give in, never give in, never, never, never, never." to "Winston Churchill",
        "The credit belongs to the man who is actually in the arena, whose face is marred by dust and sweat and blood." to "Theodore Roosevelt",
        "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
        "A journey of a thousand miles begins with a single step." to "Lao Tzu",
        "It does not matter how slowly you go as long as you do not stop." to "Confucius",
        "Well begun is half done." to "Aristotle",
        "The beginning is the most important part of the work." to "Plato",
        "Chance favors only the prepared mind." to "Louis Pasteur",
        "Nothing great was ever achieved without enthusiasm." to "Ralph Waldo Emerson",
        "Do the thing and you will have the power." to "Ralph Waldo Emerson",
        "Start where you are. Use what you have. Do what you can." to "Arthur Ashe",
        "You must do the thing you think you cannot do." to "Eleanor Roosevelt",
        "It always seems impossible until it's done." to "Nelson Mandela",
        "Courage is not the absence of fear, but the triumph over it." to "Nelson Mandela",
        "If you can't fly then run, if you can't run then walk, if you can't walk then crawl, but whatever you do you have to keep moving forward." to "Martin Luther King Jr.",
        "Faith is taking the first step even when you don't see the whole staircase." to "Martin Luther King Jr.",
        "Nothing will work unless you do." to "Maya Angelou",
        "You may encounter many defeats, but you must not be defeated." to "Maya Angelou",
        "Act as if what you do makes a difference. It does." to "William James",
        "The important thing is not to stop questioning." to "Albert Einstein",
        "Try not to become a man of success, but rather try to become a man of value." to "Albert Einstein",
        "Nothing in life is to be feared, it is only to be understood. Now is the time to understand more, so that we may fear less." to "Marie Curie",
        "I attribute my success to this: I never gave or took any excuse." to "Florence Nightingale",
        "Any man could, if he were so inclined, be the sculptor of his own brain." to "Santiago Ramon y Cajal",
        "You can't ever reach perfection, but you can believe in an asymptote toward which you are ceaselessly striving." to "Paul Kalanithi",
        "Life is short, the art long, opportunity fleeting, experience treacherous, judgment difficult." to "Hippocrates",
        "The best preparation for tomorrow is to do today's work superbly well." to "William Osler",
        "The good physician treats the disease; the great physician treats the patient who has the disease." to "William Osler",
        "Medicine is a science of uncertainty and an art of probability." to "William Osler",
        "Listen to your patient, he is telling you the diagnosis." to "William Osler",
        "Wherever the art of Medicine is loved, there is also a love of Humanity." to "Hippocrates",
        "I am still learning." to "Michelangelo",
        "Knowledge is of no value unless you put it into practice." to "Anton Chekhov",
        "Fortune favors the bold." to "Virgil",
        "Great deeds come in proportion to the resolve of those who strive." to "Al-Mutanabbi",
        "You can tell whether a man is clever by his answers. You can tell whether a man is wise by his questions." to "Naguib Mahfouz",
        "Everyone thinks of changing the world, but no one thinks of changing himself." to "Leo Tolstoy",
        "Courage is grace under pressure." to "Ernest Hemingway",
        "A book must be the axe for the frozen sea within us." to "Franz Kafka",
        "It is impossible to live without failing at something, unless you live so cautiously that you might as well not have lived at all." to "J.K. Rowling",
        "The first principle is that you must not fool yourself, and you are the easiest person to fool." to "Richard Feynman",
        "However difficult life may seem, there is always something you can do and succeed at." to "Stephen Hawking",
        "Well done is better than well said." to "Benjamin Franklin",
        "Courage starts with showing up and letting ourselves be seen." to "Brene Brown",
        "The best way out is always through." to "Robert Frost",
        "Success is the sum of small efforts, repeated day in and day out." to "Robert Collier",
        "The future belongs to those who prepare for it today." to "Malcolm X",
        "In the midst of winter, I found there was, within me, an invincible summer." to "Albert Camus",
        "The struggle itself toward the heights is enough to fill a man's heart." to "Albert Camus"
    )

    private val rest = listOf(
        "Almost everything will work again if you unplug it for a few minutes, including you." to "Anne Lamott",
        "Rest is not idleness, and to lie sometimes on the grass under trees on a summer's day is by no means a waste of time." to "John Lubbock",
        "Take rest; a field that has rested gives a bountiful crop." to "Ovid",
        "One cannot think well, love well, sleep well, if one has not dined well." to "Virginia Woolf",
        "If you have a garden and a library, you have everything you need." to "Cicero",
        "Nature does not hurry, yet everything is accomplished." to "Lao Tzu",
        "Adopt the pace of nature: her secret is patience." to "Ralph Waldo Emerson",
        "Walking is man's best medicine." to "Hippocrates",
        "All truly great thoughts are conceived while walking." to "Friedrich Nietzsche",
        "Smile, breathe and go slowly." to "Thich Nhat Hanh",
        "Breath is the bridge which connects life to consciousness." to "Thich Nhat Hanh",
        "To the mind that is still, the whole universe surrenders." to "Lao Tzu",
        "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning." to "Albert Einstein",
        "Look up at the stars and not down at your feet." to "Stephen Hawking",
        "Look deep into nature, and then you will understand everything better." to "Albert Einstein",
        "The greatest weapon against stress is our ability to choose one thought over another." to "William James"
    )

    private val calm = listOf(
        "You are the sky. Everything else is just the weather." to "Pema Chodron",
        "Feelings come and go like clouds in a windy sky. Conscious breathing is my anchor." to "Thich Nhat Hanh",
        "The only way to make sense out of change is to plunge into it, move with it, and join the dance." to "Alan Watts",
        "Pain is inevitable. Suffering is optional." to "Haruki Murakami",
        "Let everything happen to you: beauty and terror. Just keep going. No feeling is final." to "Rainer Maria Rilke",
        "The wound is the place where the Light enters you." to "Rumi",
        "Your pain is the breaking of the shell that encloses your understanding." to "Khalil Gibran",
        "If you are distressed by anything external, the pain is not due to the thing itself, but to your estimate of it; and this you have the power to revoke at any moment." to "Marcus Aurelius",
        "It's not what happens to you, but how you react to it that matters." to "Epictetus",
        "The mind is its own place, and in itself can make a heaven of hell, a hell of heaven." to "John Milton",
        "What we fear doing most is usually what we most need to do." to "Tim Ferriss",
        "Pain and suffering are always inevitable for a large intelligence and a deep heart." to "Fyodor Dostoevsky",
        "We suffer more often in imagination than in reality." to "Seneca",
        "This too shall pass." to "Persian proverb",
        "Fall seven times, stand up eight." to "Japanese proverb"
    )

    private val all = focus + rest + calm

    fun pick(seed: Int): Pair<String, String> = all[Math.floorMod(seed, all.size)]
    fun random(): Pair<String, String> = all.random()
    fun focus(): Pair<String, String> = focus.random()
    fun rest(): Pair<String, String> = rest.random()
    fun calm(): Pair<String, String> = calm.random()

    fun fmt(p: Pair<String, String>): String = "\u201C${p.first}\u201D\n\u2014 ${p.second}"

    fun line(): String = fmt(random())
    fun focusLine(): String = fmt(focus())
    fun restLine(): String = fmt(rest())
    fun calmLine(): String = fmt(calm())
}
